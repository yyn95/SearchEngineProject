/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The dictionary file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();

    public static final int ENTRYSIZE = 46;

    private long skip = TABLESIZE / 10L;

    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public class Entry {
        long data_pos;
        int PL_size;

        public Entry(long data_pos, int PL_size){
            this.data_pos = data_pos;
            this.PL_size = PL_size;
        }
    }

    // ==================================================================

    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */
    int writeData(String word, String dataString, long ptr ) {
        try {
            dataFile.seek( ptr );
            byte[] w = word.getBytes();
            byte[] data = dataString.getBytes();
            dataFile.write(w);
            dataFile.write( data );
            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     *  Reads data from the data file
     */
    String readData( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================================================================

    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry( Entry entry, long ptr ) {
        //
        //  YOUR CODE HERE
        //
        try{
            dictionaryFile.seek(ptr);
            dictionaryFile.writeLong(entry.data_pos);
            dictionaryFile.writeLong(entry.PL_size);
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    boolean checkWord(long ptr, String word){
        try{
            dataFile.seek(ptr);
            byte[] w = new byte[word.length()];
            dataFile.readFully(w);
            return (word.equals(new String(w)));
        }
        catch ( IOException e ) {
            e.printStackTrace();
            return false;
        }
    }

    Entry readEntry( long ptr ) {
        long data_ptr = 0L;
        int PL_size = 0;
        try{
            dictionaryFile.seek(ptr);
            data_ptr = dictionaryFile.readLong();
            PL_size = (int)dictionaryFile.readLong();
        }
        catch (IOException e){
            e.printStackTrace();
        }
        return (new Entry(data_ptr,PL_size));
    }


    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }

    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put(new Integer(data[0]), data[1]);
                docLengths.put(new Integer(data[0]), new Integer(data[2]));
            }
        }
        freader.close();
    }


    /**
     *  Write the index to files.
     */

    /*Hash function*/

    private long getNewHash(long hashvalue) { return (hashvalue + skip) % TABLESIZE; }

    public void writeIndex() {
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();

            // Write the dictionary and the postings list
            int count = 0;

            boolean[] occupied = new boolean[(int)TABLESIZE];
            for(Map.Entry<String,PostingsList> entry: index.entrySet()){
                long h = Math.abs(entry.getKey().hashCode()) % TABLESIZE;

                while(occupied[(int)h]){
                    collisions++;
                    h = getNewHash(h);
                }
                count++;
                if(count % 10000 == 0) System.err.println("Saved " +count+ " indexes");
                occupied[(int)h] = true;
                int num_bytes = writeData(entry.getKey(),entry.getValue().toStr(),free);
                writeEntry(new Entry(free, num_bytes),h*(ENTRYSIZE));
                free += (entry.getKey().length() + num_bytes);

            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }

    // ==================================================================

    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        long hash_v = Math.abs(token.hashCode()) % TABLESIZE;
        Entry entry = readEntry(hash_v * ENTRYSIZE);
        while(!checkWord(entry.data_pos, token)){
            hash_v = getNewHash(hash_v);
            entry = readEntry(hash_v * ENTRYSIZE);
        }
        return (new PostingsList(readData(entry.data_pos + token.length(), entry.PL_size)));
    }

    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        if(!index.containsKey(token)){
            PostingsList new_postings = new PostingsList();
            new_postings.add(docID, offset);
            index.put(token, new_postings);
        }
        index.get(token).add(docID, offset);
    }

    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk...\n" );
        writeIndex();
        System.err.println( "done!");
    }

}
