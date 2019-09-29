/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.*;
import ir.Query.QueryTerm;

public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }

        @Override
        public boolean equals(Object obj) {
            if (((KGramStat)obj).getToken().equals(token)) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  szA, the size of set B is szB and the intersection
     *  of the two sets contains intersection elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        return (double)intersection / (double)(szA + szB - intersection);
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
        int s1_len = s1.length() + 1;
        int s2_len = s2.length() + 1;
        int[][] m = new int[s1_len][s2_len];

        for (int i = 0; i < s1_len; i++) { m[i][0] = i; }
        for (int i = 0; i < s2_len; i++) { m[0][i] = i; }

        for (int i = 0; i < s1_len - 1; i++) {
            for (int j = 0; j < s2_len - 1; j++) {
                int replacement_cost = (s1.charAt(i) == s2.charAt(j))?m[i][j]:(m[i][j] + 2);
                int insert_cost = m[i][j+1] + 1;
                int delete_cost = m[i+1][j] + 1;
                m[i+1][j+1] = Math.min(replacement_cost, Math.min(insert_cost, delete_cost));
            }
        }
        return m[s1_len-1][s2_len-1];
    }

    /**
     *  Checks spelling of all terms in query and returns up to
     *  limit ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {

        long startTime = System.currentTimeMillis();
        int K = kgIndex.getK();
        double nDocs = index.docNames.size();
        // store all candidates list for all query terms
        List<List<KGramStat>> qCorrections = new ArrayList();

        for (QueryTerm qt: query.queryterm){
            String token = qt.term;
            // List of candidates
            List<KGramStat> TermCorrection = new ArrayList<KGramStat>();

            // if that term has at least one doc
            if (index.getPostings(token) != null){
                TermCorrection.add(new KGramStat(token, 2.0));
                qCorrections.add(TermCorrection);
                continue;
            }

            // Map the token id to the number of intersected k grams (A∩B) with this term
            Map<Integer, Integer> IntersectNum = new HashMap<Integer, Integer>();

            // generate the kgrams
            String extended_token = '^' + token + '$';

            for(int i=0; i < extended_token.length()- K + 1; i++) {
                String kgram = extended_token.substring(i, i + K);
                List<KGramPostingsEntry> pList = kgIndex.getPostings(kgram);
                if (pList != null){
                    for (KGramPostingsEntry e: pList){
                        if (IntersectNum.containsKey(e.tokenID)){
                            IntersectNum.put(e.tokenID, IntersectNum.get(e.tokenID) + 1);
                        } else {
                            IntersectNum.put(e.tokenID, 1);
                        }
                    }
                }
            }

            // filtering the candidates (termToNumIntersect.keys)
            for (Integer cID: IntersectNum.keySet()) {
                // the number of kgrams of the wrong spelled word
                int wrongSpellNKgram = token.length() + 3 - K; // n - k + 3

                // the number of kgrams of the candidate
                String candidate = kgIndex.getTermByID(cID);
                int candNKgram = candidate.length() + 3 - K;

                int intersected = IntersectNum.get(cID);

                double JaccardValue = jaccard(wrongSpellNKgram, candNKgram, intersected);
                int editDist = editDistance(token, candidate);

                // filtering if bounded
                if (JaccardValue >= JACCARD_THRESHOLD && editDist <= MAX_EDIT_DISTANCE){
                    double score = 0;
                    score += (MAX_EDIT_DISTANCE - editDist)/(double)MAX_EDIT_DISTANCE;
                    score += JaccardValue;
                    score += index.getPostings(candidate).size() / nDocs;
                    TermCorrection.add(new KGramStat(candidate, score));
                }
            }

            qCorrections.add(TermCorrection);
        }

        if (qCorrections.size() == 0) return null;

        List<KGramStat> MergeResults = mergeCorrections(qCorrections, limit);


        List<String> result = new ArrayList<String>();
        //Collections.sort(MergeResults);
        for (int i=0; i < MergeResults.size() && i < limit; ++i){
            result.add(MergeResults.get(i).getToken());
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        System.err.println("Correction completed time: " + elapsedTime / 1000);

        // convert and return (if return null, change the Jaccard and edit distance threshold parameter)
        return result.stream().toArray(String[] ::new);

    }


    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  qCorrections into one final merging of query phrases. Returns up
     *  to limit corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        double nDocs = index.docNames.size();
        // Map token to its posting list
        Map<String, PostingsList> pListMap = new HashMap<String, PostingsList>();

        // merge begin from the first term candidates list
        Collections.sort(qCorrections.get(0));
        List<KGramStat> mergedList = qCorrections.get(0);//the first work list to be merged
        int maxMergeResultSize = limit * limit;

        for (KGramStat s: mergedList){
            pListMap.put(s.token, index.getPostings(s.token));
        }

        // merge the first two candidates list first, if size < maxSize, merge the result with the next candidates list
        for (int t = 1; t < qCorrections.size(); t++){
            List<KGramStat> workList = new ArrayList<KGramStat>();
            List<KGramStat> toMergeList = qCorrections.get(t);//the second list to add to the merged list(work list)

            for (KGramStat s1: mergedList){
                PostingsList pList1 = pListMap.get(s1.token);
                for (KGramStat s2: toMergeList){
                    PostingsList pList2 = index.getPostings(s2.token);
                    PostingsList intersectPList = pList1.intersect(pList2);
                    // link the two token into one query word
                    String token = s1.token + " " + s2.token;
                    // if the return doc number > 0, add the query to the merged list
                    if (intersectPList.size() > 0){
                        double score = s1.score + s2.score + intersectPList.size() / nDocs;
                        //if (score < 0.0) score = 0.0;
                        pListMap.put(token, intersectPList);
                        workList.add(new KGramStat(token, score));
                    }
                }
            }

            Collections.sort(workList);

            if (workList.size() <= maxMergeResultSize){
                mergedList = workList;
            } else {
                mergedList = workList.subList(0, maxMergeResultSize);
            }

        }

        return mergedList;
    }


}
