/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.*;

public class PostingsList implements Comparable<PostingsList>{
    
    /** The postings list */
    public ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();

    //construct function
    public PostingsList(){}
    public PostingsList(String s){
        String[] pes = s.split("\n");
        for(String pe : pes){
            this.list.add(new PostingsEntry(pe));
        }
    }
    public PostingsList(ArrayList<PostingsEntry> Entrylist){
        list = Entrylist;
    }

    public void add(int docID, int offset){
        if (list.size() > 0) {
            if (list.get(list.size()-1).docID == docID) {
                list.get(list.size()-1).addPosition(offset);
                return;
            }
        }
        PostingsEntry newEntry = new PostingsEntry(docID);
        newEntry.addPosition(offset);
        list.add(newEntry);
    }

    //add entry to the postingslist
    public void addEntry(PostingsEntry entry){ list.add(entry); }

    public void addEntry(int docID){ list.add(new PostingsEntry(docID)); }

    /** Number of postings in this list. */
    public int size() { return list.size(); }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) { return list.get( i ); }

    public String toStr(){
        String ret = "";
        for(PostingsEntry pe: this.list){
            ret = ret + pe.toStr() + "\n";
        }
        return ret.substring(0, ret.length()-1);
    }

    @Override
    public int compareTo(PostingsList o) {
        if (this.size() < o.size()) {
            return -1;
        } else if (this.size() > o.size()) {
            return 1;
        } else {
            return 0;
        }
    }

    /** Merge two postings and their positions */
    public PostingsList PhraseMerge(PostingsList p2) {
        PostingsList answer = new PostingsList();
        int i = 0;
        int j = 0;

        while ((i < list.size()) && (j < p2.size())) {
            if (list.get(i).docID == p2.get(j).docID) {
                answer.addEntry(MergeEntry(list.get(i), p2.get(j)));
                i++;
                j++;
            } else if (list.get(i).docID < p2.get(j).docID) {
                answer.addEntry(list.get(i));
                i++;
            } else {
                answer.addEntry(p2.get(j));
                j++;
            }
        }

        for(; i < this.list.size(); i++){
            answer.addEntry(list.get(i));
        }

        for(; j < p2.list.size(); j++){
            answer.addEntry(p2.get(j));
        }

        return answer;
    }

    /** only merge two postings*/
    public PostingsList Merge(PostingsList p2) {
        PostingsList answer = new PostingsList();
        int i = 0;
        int j = 0;

        while ((i < list.size()) && (j < p2.size())) {
            if (list.get(i).docID == p2.get(j).docID) {
                answer.addEntry(list.get(i));
                i++;
                j++;
            } else if (list.get(i).docID < p2.get(j).docID) {
                answer.addEntry(list.get(i));
                i++;
            } else {
                answer.addEntry(p2.get(j));
                j++;
            }
        }

        for(; i < this.list.size(); i++){
            answer.addEntry(list.get(i));
        }

        for(; j < p2.list.size(); j++){
            answer.addEntry(p2.get(j));
        }

        return answer;
    }

    /** Merge two postings */
    private PostingsEntry MergeEntry(PostingsEntry e1, PostingsEntry e2){
        ArrayList<Integer> positions = new ArrayList<Integer>();
        int i = 0;
        int j = 0;
        ArrayList<Integer> positions1 = e1.positions;
        ArrayList<Integer> positions2 = e2.positions;

        while((i < positions1.size()) && (j < positions2.size())){
            if(positions1.get(i) == positions2.get(j)){
                positions.add(positions1.get(i));
                i++;
                j++;
            }
            else if(positions1.get(i) < positions2.get(j)){
                positions.add(positions1.get(i));
                i++;
            }
            else{
                positions.add(positions2.get(j));
                j++;
            }
        }

        for(; i < positions1.size(); i++){
            positions.add(positions1.get(i));
        }

        for(; j < positions2.size(); j++){
            positions.add(positions2.get(j));
        }

        PostingsEntry answer = new PostingsEntry(e1);
        answer.positions = positions;
        return answer;
    }

    public PostingsList intersect (PostingsList p2){
        PostingsList answer = new PostingsList();
        int i = 0;
        int j = 0;

        while((i < list.size()) && (j < p2.size())){
            if(list.get(i).docID == p2.get(j).docID){
                answer.addEntry(list.get(i));
                i++;
                j++;
            }
            else if(list.get(i).docID < p2.get(j).docID){ i++; }
            else{j++;}
        }
        return answer;
    }

}

