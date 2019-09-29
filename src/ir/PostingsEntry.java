/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;
    public ArrayList<Integer> positions = new ArrayList<Integer>();

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }


    //reload consructed function for postings entry
    public PostingsEntry(){ }

    public PostingsEntry(int docID){ this.docID = docID; }
    public PostingsEntry(int docID, double score){
        this.docID = docID;
        this.score = score;
    }

    public PostingsEntry(PostingsEntry entry){
        this.docID = entry.docID;
        this.score = entry.score;
        this.positions = entry.positions;
    }

    public PostingsEntry(String s){
        String[] offsets = s.split(" ");
        this.docID = Integer.parseInt(offsets[0]);
        for(int i=1; i<offsets.length; i++){
            this.positions.add(Integer.parseInt(offsets[i]));
        }
    }

    public int size(){ return positions.size(); }
    public int get(int i){
        return this.positions.get(i);
    }

    public int get_tf() {
        return positions.size();
    }

    //add position to the doc entry
    public void addPosition(int offset){ this.positions.add(offset); }

    String toStr(){
        String ret = Integer.toString(this.docID);
        for(int offset: this.positions){
            ret = ret + " " + Integer.toString(offset);
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if(this.docID == ((PostingsEntry)obj).docID) {
            return true;
        } else {
            return false;
        }
    }
}

