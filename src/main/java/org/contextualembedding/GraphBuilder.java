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
    
    void cross(Tweets tweets, Tweet that) {
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
                wpw.add(a.term, b.term, a.cross(tweets.mat.v, b));
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
    
    void constructGraph() {
        int numTweets = tweets.size();
        
        for (int i=0; i < numTweets-1; i++) {
            Tweet a = tweets.get(i);
            
            for (int j=i+1; j < numTweets; j++) {
                Tweet b = tweets.get(j);
                
                a.cross(this, b);
            }    
        }
    }
    
}

public class GraphBuilder {
    DocTermMatrix dtmat;
    String fileName;
    String mode;
    float headp;
    float tailp;
    String outFile;
    WordPairWts wpw;
    Tweets[] lexPartition;
    
    public GraphBuilder(String fileName, String mode, float headp, float tailp, String outFile) {
        this.fileName = fileName;
        this.outFile = outFile;
        this.headp = headp;
        this.tailp = tailp;
        this.mode = mode;
        wpw = new WordPairWts();
    }
    
    public void constructGraph() throws Exception {
        loadTweets();
        
        // Graph output writer
        FileWriter fw = new FileWriter(outFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (int i=0; i < lexPartition.length; i++) {
            lexPartition[i].constructGraph();
        }
        
        // save graph
        wpw.write(bw);
        
        bw.close();
        fw.close(); 
    }
    
    public void loadTweets() throws Exception {
        dtmat = new DocTermMatrix(fileName, headp, tailp);
        
        lexPartition = new Tweets[dtmat.maxClusterId+1];
        for (int i=0; i < lexPartition.length; i++) {
            lexPartition[i] = new Tweets(this.dtmat, wpw);
        }

        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        String line;

        while ((line = br.readLine()) != null) {
            Tweet t = new Tweet(line);
            lexPartition[t.clusterId].add(t);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("usage: java GraphBuilder <input text file (each line a document)> <mode - (t/l/tl)> <head %-le> <tail %-le> <out file>");
            return;
        }        
        String inputFile = args[0];
        String mode = args[1];
        float headp = Float.parseFloat(args[2])/100;
        float tailp = Float.parseFloat(args[3])/100;        
        String oFile = args[4];
        
        try {
            GraphBuilder gb = new GraphBuilder(inputFile, mode, headp, tailp, oFile);
            gb.constructGraph();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
