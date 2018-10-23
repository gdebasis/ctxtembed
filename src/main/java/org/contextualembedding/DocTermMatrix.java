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
class TermFreq implements Comparable<TermFreq> {
    String term;
    int termId;
    int freq;
    
    static final float LAMBDA = 0.6f;
    static final float ALPHA = LAMBDA/(1-LAMBDA);

    TermFreq(TermFreq that) {
        term = that.term;
        this.termId = that.termId;
        freq = that.freq;
    }
    
    TermFreq(int termId, Vocab v) {
        this.termId = termId;
        this.term = v.getTerm(termId);
    }

    @Override
    public int compareTo(TermFreq that) {
        return Integer.compare(freq, that.freq);
    }
    
    float cross(Vocab v, TermFreq that) {
        int this_tf = this.freq;
        int that_tf = that.freq;
        float this_idf = (float)Math.log(v.getCollectionSize()/v.getCollFreq(term));
        float that_idf = (float)Math.log(v.getCollectionSize()/v.getCollFreq(that.term));
        
        return (float)(Math.log(1 + ALPHA * this_tf*this_idf) + Math.log(1 + ALPHA * that_tf*that_idf));
    }
}

class Vocab {

    String fileName;
    int termId = 0;
    long collectionSize;
    int numDocs;
    HashMap<String, TermFreq> termIdMap;
    HashMap<Integer, String> idToStrMap;

    // Quick and dirty way to specify some more stopwords (if your preprocessor has missed some)
    static final String[] stopwords = {"br", "html", "http", "www", "htmlhttpwww", "linkshttpwww", "com]"};

    Vocab(String fileName) {
        this.fileName = fileName;
        termIdMap = new HashMap<>();
        idToStrMap = new HashMap<>();
        collectionSize = 0;
    }

    boolean isStopword(String word) {
        for (String stp : stopwords) {
            if (word.equals(stp)) {
                return true;
            }
        }
        return false;
    }

    int buildVocab() throws Exception {
        FileReader fr = new FileReader(fileName);
        BufferedReader br = new BufferedReader(fr);
        TermFreq tf = null;

        String line;
        int maxClusterId = 0, clusterId;
        
        while ((line = br.readLine()) != null) {
            String[] lineSplitTokens = line.split("\\t");
            clusterId = Integer.parseInt(lineSplitTokens[1]);
            if (clusterId > maxClusterId)
                maxClusterId = clusterId;
            
            line = lineSplitTokens[4];
            
            String[] tokens = line.split("\\s+");

            for (String token : tokens) {
                if (isStopword(token)) {
                    continue;
                }

                if (!termIdMap.containsKey(token)) {
                    tf = new TermFreq(termId, this);
                    tf.freq++;
                    termIdMap.put(token, tf);
                    idToStrMap.put(new Integer(termId), token);
                    termId++;
                } else {
                    tf = termIdMap.get(token);
                    tf.freq++;  // collection freq
                }
            }
            
            collectionSize += tokens.length;
            numDocs++;
        }

        System.out.println(String.format("Initialized vocabulary comprising %d terms", termId));
        br.close();
        fr.close();
        return maxClusterId;
    }

    void pruneVocab(float headp, float tailp) {
        System.out.println("Pruning vocabulary...");

        int maxTf = 0;
        for (TermFreq tf : termIdMap.values()) {
            if (tf.freq > maxTf) {
                maxTf = tf.freq;
            }
        }

        int minCutOff = (int) (maxTf * headp);
        int maxCutOff = (int) (maxTf * tailp);

        System.out.println("Removing words with freq lower than " + minCutOff + " and higher than " + maxCutOff);

        Iterator<Map.Entry<String, TermFreq>> iter = termIdMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TermFreq> entry = iter.next();
            TermFreq tf = entry.getValue();
            if (tf.freq <= minCutOff || tf.freq >= maxCutOff) {
                iter.remove();
                idToStrMap.remove(tf.termId);
            }
        }

        System.out.println("vocab reduced to size " + termIdMap.size());
    }

    int getTermId(String word) {
        return termIdMap.containsKey(word) ? termIdMap.get(word).termId : -1;
    }

    TermFreq getTermFreq(String word) {
        return termIdMap.get(word);
    }

    String getTerm(int id) {
        return idToStrMap.get(id);
    }

    int vocabSize() {
        return termId;
    }
    
    int getNumDocs() { return numDocs; }
    
    long getCollectionSize() { return this.collectionSize; }
    
    int getCollFreq(String word) {
        return termIdMap.get(word).freq;
    }
}

class TfVector {
    List<TermFreq> buff;

    public TfVector(HashMap<Integer, TermFreq> tfvec) {
        buff = new ArrayList<>();
        for (TermFreq tf : tfvec.values()) {
            buff.add(tf);
        }
    }
    
    int size() { return buff.size(); }
    TermFreq get(int i) { return buff.get(i); }
}

public class DocTermMatrix {

    String fileName;
    Vocab v;
    HashMap<Integer, TermFreq> tfvec;
    int maxClusterId;

    static final int MAX_DOC_SIZE = 500;

    DocTermMatrix(String fileName, float headp, float tailp) {
        this.fileName = fileName;

        try {
            v = new Vocab(fileName);
            maxClusterId = v.buildVocab();
            v.pruneVocab(headp, tailp);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    TfVector vectorize(String line) {
        tfvec = new HashMap<>();

        String[] tokens = line.split("\\s+");

        for (int i = 0; i < tokens.length; i++) {
            int termId = v.getTermId(tokens[i]);
            if (termId == -1) {
                continue;
            }

            TermFreq seenTf = tfvec.get(termId);
            if (seenTf == null) {
                seenTf = new TermFreq(termId, v);
                tfvec.put(termId, seenTf);
            }
            seenTf.freq++;
        }
        return new TfVector(tfvec);
    }
}
