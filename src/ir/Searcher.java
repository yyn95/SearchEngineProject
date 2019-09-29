/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.*;
import java.lang.Math;
import ir.Query.QueryTerm;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    /** add: from docName to docID */
    private HashMap<String, Integer> docIDs = new HashMap<String, Integer>();

    /** add: intialize the HITSRanker only one time in Engine */
    HITSRanker hr;

    /**
     * add: build docIDs, need to add to Engine to initialize
     */
    public void buildNameToID() {
        for (Map.Entry<Integer, String> entry: index.docNames.entrySet()) {
            docIDs.put(getFileName(entry.getValue()), entry.getKey());
        }
    }

    private String getFileName( String path ) {
        int index = path.lastIndexOf("\\");
        String result = path.substring(index + 1, path.length());
        return result;
    }


    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
    }


    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType ) {
        switch(queryType) {
            case INTERSECTION_QUERY:
                return intersection_query(query);
            case PHRASE_QUERY:
                return phrase_query(query);
            case RANKED_QUERY:
                return rank_query(query, rankingType);
            default:
                return null;
        }
    }



    /**
     *  rank_search
     */
    private PostingsList rank_query(Query query, RankingType rankingType) {
        if(rankingType == RankingType.HITS){
            return HITS_search(query);
        }
        else{
            return rank_search(query, rankingType);
        }
    }

    //for rankingType: HITS
    private PostingsList HITS_search(Query query){
        //processing wildcard query
        ArrayList<QueryTerm> newQueryTerms = new ArrayList<>();
        for (QueryTerm qt: query.queryterm) {
            // Skip the term if it doesn't contain an asterisk *
            if (!qt.term.contains("*")) {
                newQueryTerms.add(qt);
                continue;
            }

            String extended_token = "^".concat(qt.term).concat("$"); // for the later matching by regex

            HashSet<String> TermCandidates = kgIndex.getWCTokens(extended_token);

            for (String candidate : TermCandidates) {
                newQueryTerms.add(query.new QueryTerm(candidate, 1.0));
            }
        }
        query.queryterm = newQueryTerms;

        //build web subset
        ArrayList<String> uniqueTerm = new ArrayList();

        //base set: contains all relevant file names
        HashSet<String> baseSet = new HashSet<String>();

        //calulate root set first
        PostingsList tList = null;
        for (Query.QueryTerm qterm: query.queryterm) {
            String curTerm = qterm.term;
            if (uniqueTerm.contains(curTerm)) {
                continue;
            }
            tList = index.getPostings(curTerm);
            for(PostingsEntry entry: tList.list){
                baseSet.add(getFileName(index.docNames.get(entry.docID)));
            }
            uniqueTerm.add(curTerm);
        }

        //add outlinks set and inlinks set to base set
        String[] rootTitles = baseSet.toArray(new String[0]);

        for(String title : rootTitles){
            //outlinks
            HashMap<Integer,Boolean> outlinks = hr.link.get(hr.titleToId.get(title));
            if(outlinks != null){
                for(Map.Entry<Integer, Boolean> outlink: outlinks.entrySet()){
                    String linkname = hr.IdTotitle.get(outlink.getKey());
                    if(docIDs.get(linkname) != null){
                        baseSet.add(linkname);
                    }
                }
            }

            //fromlinks
            HashMap<Integer,Boolean> fromlinks = hr.fromlink.get(hr.titleToId.get(title));
            if(fromlinks != null){
                for(Map.Entry<Integer, Boolean> fromlink: fromlinks.entrySet()){
                    String linkname = hr.IdTotitle.get(fromlink.getKey());
                    if(docIDs.get(linkname) != null){
                        baseSet.add(linkname);
                    }
                }
            }

        }

        System.out.println(baseSet.size());
        //set the max iteration value (may not converge)
        hr.MAX_NUMBER_OF_STEPS = 10;
        //calculate hubs and authorities
        hr.rank(baseSet);

        //write result score into postings entrys
        ArrayList<PostingsEntry> list = new ArrayList();
        double score;
        for(String title : baseSet){
            score = 0.5 * hr.hubs.get(hr.titleToId.get(title)) + 0.5 * hr.authorities.get(hr.titleToId.get(title));
            //result_Map.put(docIDs.get(title), new PostingsEntry(docIDs.get(title), score));
            list.add(new PostingsEntry(docIDs.get(title), score));
        }
        Collections.sort(list);
        PostingsList result = new PostingsList(list);
        return result;

    }

    //for rankingType: tf-idf, PageRank, Combination
    private PostingsList rank_search(Query query, RankingType rankingType) {
        //get a new one wildcard query
        ArrayList<QueryTerm> newQueryTerms = new ArrayList<>();

        for (QueryTerm qt: query.queryterm) {
            // Skip the term if it doesn't contain an asterisk *
            if (!qt.term.contains("*")) {
                newQueryTerms.add(qt);
                continue;
            }

            String extended_token = "^".concat(qt.term).concat("$"); // for the later matching by regex

            HashSet<String> TermCandidates = kgIndex.getWCTokens(extended_token);

            for (String candidate : TermCandidates) {
                newQueryTerms.add(query.new QueryTerm(candidate, 1.0));
            }
        }

        // rank search new query
        HashSet<String> uniqueTerm = new HashSet<>();
        //for all returned docs: docID to entry
        HashMap<Integer, PostingsEntry> curMap = new HashMap();

        query.queryterm = newQueryTerms;

        for (QueryTerm qterm: newQueryTerms) {
            String curTerm = qterm.term;
            if (uniqueTerm.contains(curTerm)) {
                continue;
            }
            //the Postingslist of current term
            PostingsList tList = index.getPostings(curTerm);

            // compute tf and idf of the query vector
            //int tf_query = query.get_tf(curTerm);
            // suppose there are no repeats
            int tf_query = 1;
            double idf;
            if (tList == null) {
                //begin spell correction
                return null;
            } else {
                idf = Math.log10(index.docNames.size() / tList.size());
            }

            for (int j = 0; j < tList.size(); j++) {
                int curDocID = tList.get(j).docID;
                if (curMap.containsKey(curDocID)) {
                    //also consider the weight from the relevance feedback
                    curMap.get(curDocID).score += qterm.weight * tf_query * idf * tList.get(j).get_tf();
                } else {
                    tList.get(j).score += qterm.weight * tf_query * idf * tList.get(j).get_tf();
                    curMap.put(curDocID, tList.get(j));
                }
            }
            uniqueTerm.add(curTerm);
        }


        if (rankingType == RankingType.TF_IDF) {
            for (int docID: curMap.keySet()) {
                // calculate the tf-idf score
                curMap.get(docID).score /= index.docLengths.get(docID);
            }
        }
        else if (rankingType == RankingType.PAGERANK) {
            for (int docID: curMap.keySet()) {
                // calculate the combined score (tf-idf and pagerank)
                // if docID is not in PR map add 0
                curMap.get(docID).score = (PageRank_map.containsKey(docID))? PageRank_map.get(docID):0;
            }
        }
        else if(rankingType == RankingType.COMBINATION){
            // there might be better ways to combine two factors
            double combination = 0.5;
            for (int docID: curMap.keySet()) {
                // calculate the tf-idf score
                curMap.get(docID).score /= index.docLengths.get(docID);
                // calculate the combined score (tf-idf and pagerank)
                if(PageRank_map.containsKey(docID)){
                    curMap.get(docID).score = combination * (curMap.get(docID).score) + (1 - combination) * PageRank_map.get(docID);
                }
                else{
                    curMap.get(docID).score = combination * (curMap.get(docID).score);
                }
            }
        }


        //convert Hashmap to ArrayList and sort the documents based on the scores
        ArrayList<PostingsEntry> list = new ArrayList(curMap.values());
        Collections.sort(list);
        PostingsList result = new PostingsList(list);

        return result;
    }


    /**
     * read pagerank file, need to add to Engine to initialize
     */

    /** Mapping from file docID to pagerank */
    HashMap<Integer, Double> PageRank_map = new HashMap<Integer, Double>();

    public void BuildPageRank() {
        String fileName = "PR/pagerank";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] strs = line.split(";");
                PageRank_map.put(docIDs.get(strs[0]), Double.valueOf(strs[1]));
            }
            reader.close();
            System.out.println("PageRank loaded!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     *  intersection_search
     */
    public PostingsList intersection_query(Query query){
        ArrayList<PostingsList> all_lists = new ArrayList<PostingsList>();

        for (QueryTerm qt: query.queryterm) {
            // Skip the term if it doesn't contain an asterisk *
            if (!qt.term.contains("*")) {
                all_lists.add(index.getPostings(qt.term));
                continue;
            }

            String extended_token = "^".concat(qt.term).concat("$"); // for the later matching by regex

            HashSet<String> TermCandidates = kgIndex.getWCTokens(extended_token);
            System.out.println(qt.term + ": " + TermCandidates.size());
            ArrayList<PostingsList> SortList = new ArrayList<>();
            for (String candidate : TermCandidates) {
                SortList.add(index.getPostings(candidate));
            }
            Collections.sort(SortList);

            PostingsList MergeList = SortList.get(0);
            if(SortList.size() > 1){
                for(int i = 1; i < SortList.size();i++){
                    MergeList = MergeList.Merge(SortList.get(i));
                }
            }
            all_lists.add(MergeList);
        }

        if(all_lists.size() == 1){
            return all_lists.get(0);
        }
        else{
            PostingsList result = null;
            if(!all_lists.contains(null)){
                result = all_lists.get(0);
                for(int i = 1; i < all_lists.size(); i++){
                    result = intersect(result, all_lists.get(i));
                }
            }
            return result;
        }
    }

    public PostingsList intersect (PostingsList p1, PostingsList p2){
        PostingsList answer = new PostingsList();
        int i = 0;
        int j = 0;

        while((i < p1.size()) && (j < p2.size())){
            if(p1.get(i).docID == p2.get(j).docID){
                answer.addEntry(p1.get(i));
                i++;
                j++;
            }
            else if(p1.get(i).docID < p2.get(j).docID){ i++; }
            else{j++;}
        }
        return answer;
    }


    /**
     *  phrase_search
     */
    public PostingsList phrase_query(Query query){
        ArrayList<PostingsList> all_lists = new ArrayList<PostingsList>();

        for (QueryTerm qt: query.queryterm) {
            // Skip the term if it doesn't contain an asterisk *
            if (!qt.term.contains("*")) {
                all_lists.add(index.getPostings(qt.term));
                continue;
            }

            String extended_token = "^".concat(qt.term).concat("$"); // for the later matching by regex

            HashSet<String> TermCandidates = kgIndex.getWCTokens(extended_token);

            ArrayList<PostingsList> SortList = new ArrayList<>();
            for (String candidate : TermCandidates) {
                SortList.add(index.getPostings(candidate));
            }
            Collections.sort(SortList);

            PostingsList MergeList = SortList.get(0);
            if(SortList.size() > 1){
                for(int i = 1; i < SortList.size();i++){
                    MergeList = MergeList.PhraseMerge(SortList.get(i));
                }
            }
            all_lists.add(MergeList);
        }

        if(all_lists.size() == 1){
            return all_lists.get(0);
        }
        else{
            PostingsList result = null;
            if(!all_lists.contains(null)){
                result = all_lists.get(0);
                for(int i = 1; i < all_lists.size(); i++){
                    result = phrase_intersect(result, all_lists.get(i));
                }
            }
            return result;
        }
    }

    private PostingsList phrase_intersect(PostingsList p1, PostingsList p2){
        PostingsList answer = new PostingsList();
        int i = 0;
        int j = 0;

        while((i < p1.size()) && (j < p2.size())){
            if(p1.get(i).docID == p2.get(j).docID){
                PostingsEntry resultEntry = intersect_position(p1.get(i),p2.get(j));
                if(resultEntry.size() != 0){
                    answer.addEntry(resultEntry);
                }
                i++;
                j++;
            }
            else if(p1.get(i).docID < p2.get(j).docID){ i++; }
            else{ j++; }
        }
        return answer;
    }

    private PostingsEntry intersect_position(PostingsEntry e1, PostingsEntry e2){
        PostingsEntry answer = new PostingsEntry(e1.docID);
        int i = 0;
        int j = 0;

        while((i < e1.size()) && (j < e2.size())){
            if(e1.get(i) == e2.get(j) - 1){
                answer.addPosition(e2.get(j));
                i++;
                j++;
            }
            else if(e1.get(i) < e2.get(j) - 1){ i++; }
            else{ j++; }
        }
        return answer;
    }


}


