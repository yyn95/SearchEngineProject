/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.*;
import java.nio.charset.*;
import java.io.*;

import static java.util.stream.Collectors.toMap;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
        String term;
        double weight;
        QueryTerm( String t, double w ) {
            term = t;
            weight = w;
        }
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.2;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
        StringTokenizer tok = new StringTokenizer( queryString );
        while ( tok.hasMoreTokens() ) {
            queryterm.add( new QueryTerm(tok.nextToken(), 1.0) );
        }    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
        return queryterm.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for ( QueryTerm t : queryterm ) {
            len += t.weight; 
        }
        return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for ( QueryTerm t : queryterm ) {
            queryCopy.queryterm.add( new QueryTerm(t.term, t.weight) );
        }
        return queryCopy;
    }

    public int get_tf(String token) {
        int count = 0;
        for (QueryTerm qTerm: queryterm) {
            if (qTerm.term.equals(token)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Engine engine ) {
        if (results == null) {
            return;
        }
        if (results.list.isEmpty()) {
            return;
        }

        //get all relevant docID
        HashSet<Integer> relevantDocs = new HashSet<Integer>();
        for(int i = 0; i<docIsRelevant.length; i++){
            if(docIsRelevant[i]) {
                relevantDocs.add(results.get(i).docID);
            }
        }

        if(relevantDocs.size() == 0) return;

        //double norm_alpha = this.alpha / (double)queryterm.size();
        //not normalize the alpha to make this item weight more
        double norm_alpha = this.alpha;
        double norm_beta = this.beta / (double) relevantDocs.size();

        //store all potential query terms and its weight
        HashMap<String, Double> uniqTerms = new HashMap<String, Double>();

        //the query and all relevant docs use tf-idf vector
        for (QueryTerm tTerm: queryterm) {
            uniqTerms.put(tTerm.term, norm_alpha * get_tf(tTerm.term) * Math.log10((double)engine.index.docNames.size()/engine.index.getPostings(tTerm.term).size()));
        }

        //only consider relevant docs
        //all token in all relevant docs will be added to uniqTerms as potential query terms
        for(int relevantID: relevantDocs){
            int doc_len = engine.index.docLengths.get(relevantID);
            double length_norm_beta = norm_beta / (double)doc_len;
            //read the document again and add all tokens and its weight to hashmap
            try{
                Reader reader = new InputStreamReader( new FileInputStream(engine.index.docNames.get(relevantID)), StandardCharsets.UTF_8 );
                Tokenizer tok = new Tokenizer( reader, true, false, true, engine.patterns_file );

                while(tok.hasMoreTokens()){
                    String token = tok.nextToken();
                    //use 1* idf (tf=1), as it will be added many times if the token occur multiple times in this doc
                    double tf_idf = length_norm_beta * Math.log10((double)engine.index.docNames.size()/engine.index.getPostings(token).size());

                    if(uniqTerms.get(token) != null){
                        double newweight = uniqTerms.get(token) + tf_idf;
                        //newweight = newweight > 0? newweight:0;
                        uniqTerms.put(token,newweight);
                    }
                    else{
                        //double newweight = tf_idf > 0 ? tf_idf:0;
                        //uniqTerms.put(token, newweight);
                        uniqTerms.put(token, tf_idf);
                    }
                }
                reader.close();
            }
            catch (IOException e){
                System.err.println( "Warning: IOException during relevance feedback." );
            }

        }

        //sort by value of each vector entry in a descending way
        Map<String,Double> sorted = uniqTerms.entrySet()
                .stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                        LinkedHashMap::new));

        //search with the new query composed of top 10 query terms
        queryterm.clear();
        //int count = 0;
        //System.out.println("Top 10 weighted query token");
        for(Map.Entry<String,Double> entry: sorted.entrySet()){
            //count += 1;
            queryterm.add(new QueryTerm(entry.getKey(),entry.getValue()));
            //if(count == 10) break;
        }

    }
}


