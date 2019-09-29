/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import ir.Query.QueryTerm;

public class KGramIndex {

    /** Mapping from term ids to actual term strings */
    HashMap<Integer,String> id2term = new HashMap<Integer,String>();//original token, not the extended one

    /** Mapping from term strings to term ids */
    HashMap<String,Integer> term2id = new HashMap<String,Integer>();

    /** Index from k-grams to list of term ids that contain the k-gram */
    HashMap<String,List<KGramPostingsEntry>> index = new HashMap<String,List<KGramPostingsEntry>>();

    /** The ID of the last processed term */
    int lastTermID = -1;

    /** Number of symbols to form a K-gram */
    int K = 3;

    /** add: Mapping from term strings to the number of its k-grams  */
    HashMap<String, Integer> term2kgNum = new HashMap<String, Integer>();

    public KGramIndex(int k) {
        K = k;
        if (k <= 0) {
            System.err.println("The K-gram index can't be constructed for a negative K value");
            System.exit(1);
        }
    }

    /** Generate the ID for an unknown term */
    private int generateTermID() {
        return ++lastTermID;
    }

    public int getK() {
        return K;
    }


    /**
     *  Get all candidate tokens for one query term
     */
    public HashSet<String> getWCTokens(String extended_token) {

        List<KGramPostingsEntry> curTermTokens = new ArrayList();
        String[] strs = extended_token.split("\\*");

        for (String str: strs) {
            //when the str length < k, we cannot get a k-gram from it.
            if (str.length() >= K) {
                for (int i = 0; i < str.length() - K + 1; i++) {
                    String t_kgram = str.substring(i, i + K);
                    if (curTermTokens.isEmpty()) {
                        curTermTokens = index.get(t_kgram);
                    } else {
                        curTermTokens = intersect(curTermTokens, index.get(t_kgram));
                    }
                }
            }
        }

        // Filter the wrong tokens
        Pattern p = Pattern.compile(extended_token.replace("*", "\\S*"));

        // Transform the KGramPostingsEntry to QueryTerm
        HashSet<String> result = new HashSet<>();
        if(curTermTokens.size() != 0){
            for (KGramPostingsEntry entry: curTermTokens) {
                String term = getTermByID(entry.tokenID);
                if (p.matcher(term).matches()) {
                    result.add(term);
                    //System.out.println(term);
                }
            }
        }

        return result;
    }


    /**
     *  Get intersection of two postings lists
     */
    public List<KGramPostingsEntry> intersect(List<KGramPostingsEntry> p1, List<KGramPostingsEntry> p2) {

        if (p1 == null || p2 == null) {
            return new ArrayList();
        }
        if (p1.isEmpty()) {
            return p1;
        }
        if (p2.isEmpty()) {
            return p2;
        }

        List<KGramPostingsEntry> result = new ArrayList();
        int i = 0;
        int j = 0;

        while((i < p1.size()) && (j < p2.size())){
            if(p1.get(i).tokenID == p2.get(j).tokenID){
                result.add(p1.get(i));
                i++;
                j++;
            }
            else if(p1.get(i).tokenID < p2.get(j).tokenID){ i++; }
            else{j++;}
        }

        return result;

    }


    /** Inserts all k-grams from a token into the index. */
    public void insert( String token ) {
        // if the token has been indexed, return
        if (term2id.containsKey(token)) {
            return;
        }

        // use generateTermID() to get the next id(+1), add the token to term2id and id2term
        int id = generateTermID();
        id2term.put(id, token);
        term2id.put(token, id);

        KGramPostingsEntry newEntry = new KGramPostingsEntry(id);
        String extended_token = "^".concat(token).concat("$");

        int numOfGrams = extended_token.length() - K + 1;
        term2kgNum.put(token, numOfGrams);

        for (int i = 0; i < numOfGrams; i++) {
            String t_kgram = extended_token.substring(i, i + K);
            List<KGramPostingsEntry> entrys = index.get(t_kgram);

            // If this k-gram is already in the k-gram index
            if (entrys != null) {
                // a k-gram may occur many times in one token
                if (!entrys.contains(newEntry)) {
                    entrys.add(newEntry);
                }
            } else {
                entrys = new ArrayList();
                entrys.add(newEntry);
                index.put(t_kgram, entrys);
            }
        }

    }

    /** Get postings for the given k-gram */
    public List<KGramPostingsEntry> getPostings(String kgram) {
        if (index.containsKey(kgram)) {
            return index.get(kgram);
        }
        return null;
    }

    /** Get id of a term */
    public Integer getIDByTerm(String term) {
        return term2id.get(term);
    }

    /** Get a term by the given id */
    public String getTermByID(Integer id) {
        return id2term.get(id);
    }

    private static HashMap<String,String> decodeArgs( String[] args ) {
        HashMap<String,String> decodedArgs = new HashMap<String,String>();
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("patterns_file", args[i++]);
                }
            } else if ( "-f".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("file", args[i++]);
                }
            } else if ( "-k".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("k", args[i++]);
                }
            } else if ( "-kg".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("kgram", args[i++]);
                }
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }
        return decodedArgs;
    }

    public static void main(String[] arguments) throws FileNotFoundException, IOException {
        HashMap<String,String> args = decodeArgs(arguments);

        int k = Integer.parseInt(args.getOrDefault("k", "3"));
        KGramIndex kgIndex = new KGramIndex(k);

        File f = new File(args.get("file"));
        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
        Tokenizer tok = new Tokenizer( reader, true, false, true, args.get("patterns_file") );
        while ( tok.hasMoreTokens() ) {
            String token = tok.nextToken();
            kgIndex.insert(token);
        }

        String[] kgrams = args.get("kgram").split(" ");
        List<KGramPostingsEntry> postings = null;
        for (String kgram : kgrams) {
            if (kgram.length() != k) {
                System.err.println("Cannot search k-gram index: " + kgram.length() + "-gram provided instead of " + k + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(kgram);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(kgram));
            }
        }
        if (postings == null) {
            System.err.println("Found 0 posting(s)");
        } else {
            int resNum = postings.size();
            System.err.println("Found " + resNum + " posting(s)");
            if (resNum > 10) {
                System.err.println("The first 10 of them are:");
                resNum = 10;
            }
            for (int i = 0; i < resNum; i++) {
                System.err.println(kgIndex.getTermByID(postings.get(i).tokenID));
            }
        }
    }
}
