/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;

import java.util.*;
import java.io.*;
import java.util.Collections;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    public int MAX_NUMBER_OF_STEPS = 1000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.001;

    /**
     *   The inverted index
     */
    Index index;

    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String,Integer> titleToId = new HashMap<String,Integer>();

    /**
     *   Mapping from the nodeID to titles
     */
    HashMap<Integer,String> IdTotitle = new HashMap<Integer,String>();

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs = new HashMap<Integer,Double>();

    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities = new HashMap<Integer,Double>();


    //recode outlinks information from doc i
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();
    //recode outlinks information to doc i
    HashMap<Integer,HashMap<Integer,Boolean>> fromlink = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index ) {
        this.index = index;
        readDocs( linksFilename, titlesFilename );
    }


    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\/" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }


    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     */
    void readDocs( String linksFilename, String titlesFilename ) {
        BufferedReader reader = null;
        //read titles file
        try {
            System.err.print( "HITS: Reading titles file... " );
            reader = new BufferedReader(new FileReader(titlesFilename));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] strs = line.split(";");
                titleToId.put(strs[1], Integer.parseInt(strs[0]));
                IdTotitle.put(Integer.parseInt(strs[0]),strs[1]);
            }
            System.err.print( "done.\r\n" );
        } catch (Exception e) {
            e.printStackTrace();
        }

        //read links file
        try {
            System.err.print( "HITS: Reading links file... " );
            BufferedReader in = new BufferedReader( new FileReader( linksFilename ));
            String line;
            while ((line = in.readLine()) != null) {
                int index = line.indexOf( ";" );
                Integer fromdoc = Integer.parseInt(line.substring( 0, index ));

                // Check all outlinks.
                StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
                while ( tok.hasMoreTokens()) {
                    Integer otherDocID = Integer.parseInt(tok.nextToken());
                    // Set the probability to 0 for now, to indicate that there is
                    // a link from fromdoc to otherDoc.
                    if (link.get(fromdoc) == null) {
                        link.put(fromdoc, new HashMap<Integer,Boolean>());
                    }
                    if (link.get(fromdoc).get(otherDocID) == null) {
                        link.get(fromdoc).put(otherDocID, true);
                    }

                    //if there is a link from fromdoc to otherdocID
                    // then add (fromdoc, true) to fromlink.get(otherdocID)
                    if (fromlink.get(otherDocID) == null){
                        fromlink.put(otherDocID, new HashMap<Integer,Boolean>());
                    }
                    if (fromlink.get(otherDocID).get(fromdoc) == null) {
                        fromlink.get(otherDocID).put(fromdoc, true);
                    }
                }
            }
            System.err.print( "done.\r\n" );
        }
        catch ( FileNotFoundException e ) {
            System.err.println( "File " + linksFilename + " not found!" );
        }
        catch ( IOException e ) {
            System.err.println( "Error reading file " + linksFilename );
        }
    }

    /**
     * Perform HITS iterations until convergence
     *
     * @param      titles  The titles of the documents in the root set
     */
    private void iterate(String[] titles) {

        //initialize hubs and authorities with 1.0
        int num = titles.length;
        //System.out.println("subset size:" + num);

        for(int i = 0; i < num; i++){
            Integer nodeID = titleToId.get(titles[i]);
            hubs.put(nodeID, 1.0);
            authorities.put(nodeID, 1.0);
        }

        boolean hubComplete = false;
        boolean autComplete = false;
        int iteration = 0;

        while (iteration < MAX_NUMBER_OF_STEPS) {

            //update hubs
            if(!hubComplete){
                double hublength = 0;
                double hubdifference = 0;

                HashMap<Integer,Double> last_hubs = new HashMap<Integer,Double>();

                for (int i = 0; i < num; i++) {
                    Integer nodeID = titleToId.get(titles[i]);
                    HashMap<Integer,Boolean> hublink = link.get(nodeID);
                    last_hubs.put(nodeID, hubs.get(nodeID));

                    if (hublink != null) {
                        double curr_hub = 0;
                        for(Map.Entry<Integer, Boolean> entry : hublink.entrySet()){
                            Integer rowdocID = entry.getKey();
                            if(authorities.get(rowdocID) != null){
                                curr_hub += authorities.get(rowdocID);
                            }
                        }
                        hublength += curr_hub * curr_hub;
                        //hubdiff += Math.abs(curr_hub - hubs.get(nodeID));
                        hubs.put(nodeID, curr_hub);
                    }
                    else{
                        hubs.put(nodeID, 0.0);
                    }
                }

                //normalize and calculate difference
                hublength = Math.sqrt(hublength);
                for (Map.Entry<Integer, Double> entry: hubs.entrySet()) {
                    entry.setValue(entry.getValue() / hublength);
                    hubdifference += Math.abs(entry.getValue() - last_hubs.get(entry.getKey()));
                }

                //System.out.println("hub difference:"+ hubdifference);

                if (hubdifference <= EPSILON) {
                    hubComplete = true;
                    System.out.println("hub difference:"+ hubdifference);
                    System.out.println("hubs completed!");
                }
            }

            //update authorities
            if(!autComplete){
                double autlength = 0;
                double autdifference = 0;

                HashMap<Integer,Double> last_auts = new HashMap<Integer,Double>();

                for (int i = 0; i < num; i++) {
                    Integer nodeID = titleToId.get(titles[i]);
                    HashMap<Integer,Boolean> autlink = fromlink.get(nodeID);

                    //each time put a last aut value to the map
                    last_auts.put(nodeID, authorities.get(nodeID));

                    if (autlink != null) {
                        double curr_aut = 0;
                        for(Map.Entry<Integer, Boolean> entry : autlink.entrySet()){
                            Integer rowdocID = entry.getKey();
                            if(hubs.get(rowdocID) != null){
                                curr_aut += hubs.get(rowdocID);
                            }
                        }
                        autlength += curr_aut * curr_aut;
                        authorities.put(nodeID, curr_aut);
                    }
                    else{
                        authorities.put(nodeID, 0.0);
                    }
                }

                //normalize and calculate difference
                autlength = Math.sqrt(autlength);
                for (Map.Entry<Integer, Double> entry: authorities.entrySet()) {
                    entry.setValue(entry.getValue() / autlength);
                    autdifference += Math.abs(entry.getValue() - last_auts.get(entry.getKey()));
                }

                //System.out.println("auth difference:"+ autdifference);

                if (autdifference <= EPSILON) {
                    autComplete = true;
                    System.out.println("auth difference:"+ autdifference);
                    System.out.println("authorities completed!");
                }

        }

            if(hubComplete && autComplete){
                break;
            }

            iteration++;

        }

    }


    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      //post  The list of postings fulfilling a certain information need
     * subset_Map: from nodeID to entry
     * docIDToName: from docID to docname
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    void rank(HashSet<String> baseSet) {

        //clear hubs and authorities first
        hubs.clear();
        authorities.clear();

        //calculate hubs and authorites
        iterate(baseSet.toArray(new String[0]));

    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank() {
        iterate(titleToId.keySet().toArray(new String[0]));
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null );
            hr.rank();
        }
    }
}
