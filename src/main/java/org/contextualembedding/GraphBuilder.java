/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.contextualembedding;

import java.io.*;
import java.util.*;

/**
 *
 * @author dganguly
 */

class WordPairWt {
    String a;
    String b;
    float wt;

    public WordPairWt(String a, String b) {
        this.a = a;
        this.b = b;
    }
    
    void addWt(float delw) {
        wt += delw;
    }
    
    @Override
    public String toString() {
        return a + "\t" + b + "\t" + wt;
    }
}

class WordPairWts {
    HashMap<String, WordPairWt> map;

    public WordPairWts() {
        map = new HashMap<>();
    }
    
    void add(String a, String b, float delw) {
        String key = a + ":" + b;
        WordPairWt wp = map.get(key);
        if (wp == null) {
            wp = new WordPairWt(a, b);
            map.put(key, wp);
        }
        wp.addWt(delw);
    }
    
    void write(BufferedWriter bw) throws Exception {
        for (WordPairWt wt : map.values()) {
            bw.write(wt.toString());
            bw.newLine();
        }
    }
}

class Tweet {
    String text;
    String id;
    int clusterId;
    float sim;
    long epochs;

    public Tweet(String line) {
        // tweet-id, cluster-id, sim with centroid, time, text
        String[] tokens;
        tokens = line.split("\\t");
        
        this.id = tokens[0];
        this.clusterId = Integer.parseInt(tokens[1]);
        this.sim = Float.parseFloat(tokens[2]);
        this.epochs = Long.parseLong(tokens[3]);
        this.text = tokens[4];
    }
    
    void cross(Tweets tweets, Tweet that, GraphBuilder.PartitionMode mode) {
        float wt;
        WordPairWts wpw = tweets.wpw;
        TfVector avec = null, bvec = null;
        DocTermMatrix mat = tweets.mat;
        
        avec = mat.vectorize(this.text);
        bvec = mat.vectorize(that.text);
        int alen = avec.size();
        int blen = bvec.size();
        
        for (int i=0; i < alen; i++) {
            TermFreq a = avec.get(i);
            for (int j=0; j < blen; j++) {
                TermFreq b = bvec.get(j);
                if (a.termId == b.termId)
                    continue;
                
                wt = a.cross(tweets.mat.v, b);
                if (mode != GraphBuilder.PartitionMode.TEMPO)
                    wt = wt * (this.sim + that.sim);
                wpw.add(a.term, b.term, wt);
            }
        }
    }
}

class Tweets {
    DocTermMatrix mat;
    List<Tweet> tweets;
    WordPairWts wpw;

    public Tweets(DocTermMatrix mat, WordPairWts wpw) {
        this.mat = mat;
        tweets = new ArrayList<>();
        this.wpw = wpw;
    }
    
    void add(Tweet t) {
        tweets.add(t);
    }
    
    Tweet get(int i) { return tweets.get(i); }
    int getSize() { return tweets.size(); }
    
    void constructGraph(GraphBuilder.PartitionMode mode) {
        int numTweets = tweets.size();
        
        for (int i=0; i < numTweets-1; i++) {
            Tweet a = tweets.get(i);
            
            for (int j=i+1; j < numTweets; j++) {
                Tweet b = tweets.get(j);
                
                a.cross(this, b, mode);
            }    
        }
    }
    
}

public class GraphBuilder {
    DocTermMatrix dtmat;
    String fileName;
    PartitionMode mode;
    float headp;
    float tailp;
    String outFile;
    WordPairWts wpw;
    Tweets[] partition;
    int timeIntervals;
    
    enum PartitionMode { NONE, TEMPO, LEXICAL, TEMPO_LEXICAL }; 
    PartitionMode modes[] = { PartitionMode.NONE, PartitionMode.TEMPO, PartitionMode.LEXICAL, PartitionMode.TEMPO_LEXICAL};
    
    public GraphBuilder(String fileName, int mode, float headp, float tailp, String outFile, int timeIntervals) {
        this.fileName = fileName;
        this.outFile = outFile;
        this.headp = headp;
        this.tailp = tailp;
        this.mode = modes[mode];
        this.timeIntervals = timeIntervals;
        wpw = new WordPairWts();
    }
    
    public void constructGraph() throws Exception {
        loadTweets();
        
        // Graph output writer
        FileWriter fw = new FileWriter(outFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (int i=0; i < partition.length; i++) {
            if (partition[i] != null)
                partition[i].constructGraph(mode);
        }
        
        // save graph
        wpw.write(bw);
        
        bw.close();
        fw.close(); 
    }
    
    public void loadTweets() throws Exception {
        dtmat = new DocTermMatrix(fileName, headp, tailp);
        
        switch (mode) {
            case LEXICAL:
                loadTweetsLexical();
                break;
            case TEMPO:
                loadTweetsTemporal();
                break;
            default:
                loadTweetsTempoLexical();
        }
        System.out.println("Number of partitions = " + partition.length);
    }
    
    public void loadTweetsLexical() throws Exception {
        System.out.println("Lexical Partitioning...");
        
        partition = new Tweets[dtmat.maxClusterId+1];
        for (int i=0; i < partition.length; i++) {
            partition[i] = new Tweets(this.dtmat, wpw);
        }

        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;

        while ((line = br.readLine()) != null) {
            Tweet t = new Tweet(line);
            partition[t.clusterId].add(t);
        }
    }

    public void loadTweetsTemporal() throws Exception {
        System.out.println("Temporal Partitioning...");
        int partitionSize = dtmat.v.getNumDocs()/this.timeIntervals;
        System.out.println("Number of partitions: " + partitionSize);
        
        partition = new Tweets[partitionSize+1];

        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;

        int count = 0, i = 0;
        Tweets thisBatch = new Tweets(this.dtmat, wpw);
        
        while ((line = br.readLine()) != null) {
            Tweet t = new Tweet(line);
            thisBatch.add(t);
            
            count++;
            
            if (count == timeIntervals) {
                // start a new partition
                partition[i] = thisBatch;
                thisBatch = new Tweets(this.dtmat, wpw);
                count = 0;
                i++;
            }
        }
        // the remaining goes to the last batch
        if (count > 0) {
            partition[i] = thisBatch;
        }
    }
    
    private void lexPartitonBatch(HashMap<String, Tweets> map, Tweets batch, int timeInterval) {
        for (Tweet t: batch.tweets) {
            String key = timeInterval + ":" + t.clusterId;
            Tweets lexbatch = map.get(key);
            if (lexbatch == null) {
                lexbatch = new Tweets(dtmat, wpw);
                map.put(key, lexbatch);
            }
            lexbatch.add(t);
        }
    }
    
    public void loadTweetsTempoLexical() throws Exception {
        System.out.println("Tempo-lexical Partitioning...");
        
        HashMap<String, Tweets> map = new HashMap<>(); // keyed by cluster-id and time
        
        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;

        int count = 0, timeSlot = 0;
        Tweets thisBatch = new Tweets(this.dtmat, wpw);
        
        while ((line = br.readLine()) != null) {
            Tweet t = new Tweet(line);
            thisBatch.add(t);
            
            count++;
            
            if (count == timeIntervals) {
                // start a new partition
                lexPartitonBatch(map, thisBatch, timeSlot);
                thisBatch = new Tweets(this.dtmat, wpw);
                count = 0;
                timeSlot++;
            }
        }
        // remaining
        lexPartitonBatch(map, thisBatch, timeSlot);
        
        partition = new Tweets[map.size()];
        int i = 0;
        for (Tweets tws: map.values()) {
            partition[i++] = tws;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("usage: java GraphBuilder\n" 
                    + "\t<input text file (each line a document)>\n"
                    + "\t<mode - (temporal (1)/ lexical (2) /tempo-lexical (3))>\n"
                    + "\t<head %-le> <tail %-le>\n"
                    + "\t<out file>\n"
                    + "\t<#time intervals>\n");
            return;
        }        
        String inputFile = args[0];
        int mode = Integer.parseInt(args[1]);
        float headp = Float.parseFloat(args[2])/100;
        float tailp = Float.parseFloat(args[3])/100;        
        String oFile = args[4];
        int timeIntervals = Integer.parseInt(args[5]);
        
        try {
            GraphBuilder gb = new GraphBuilder(inputFile, mode, headp, tailp, oFile, timeIntervals);
            gb.constructGraph();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
