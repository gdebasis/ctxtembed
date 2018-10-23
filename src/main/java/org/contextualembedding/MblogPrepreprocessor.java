/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.contextualembedding;

/**
 *
 * @author dganguly
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.FilteringTokenFilter;
import org.json.JSONObject;
import java.text.SimpleDateFormat;

/**
 *
 * @author Debasis
 */
class WebDocAnalyzer extends Analyzer {

    CharArraySet stoplist;

    WebDocAnalyzer(CharArraySet stoplist) {
        this.stoplist = stoplist;
    }

    @Override
    protected TokenStreamComponents createComponents(String string) {
        final Tokenizer tokenizer = new UAX29URLEmailTokenizer();

        TokenStream tokenStream = new StandardFilter(tokenizer);
        tokenStream = new LowerCaseFilter(tokenStream);
        tokenStream = new StopFilter(tokenStream, stoplist);
        tokenStream = new URLFilter(tokenStream); // remove URLs
        //tokenStream = new ValidWordFilter(tokenStream); // remove words with digits
        //tokenStream = new PorterStemFilter(tokenStream);

        return new Analyzer.TokenStreamComponents(tokenizer, tokenStream);
    }
}

// Removes tokens with any digit
class ValidWordFilter extends FilteringTokenFilter {

    CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);

    public ValidWordFilter(TokenStream in) {
        super(in);
    }

    @Override
    protected boolean accept() throws IOException {
        String token = termAttr.toString();
        int len = token.length();
        for (int i = 0; i < len; i++) {
            char ch = token.charAt(i);
            if (Character.isDigit(ch)) {
                return false;
            }
            if (ch == '.') {
                return false;
            }
        }
        return true;
    }
}

class URLFilter extends FilteringTokenFilter {

    TypeAttribute typeAttr = addAttribute(TypeAttribute.class);

    public URLFilter(TokenStream in) {
        super(in);
    }

    @Override
    protected boolean accept() throws IOException {
        boolean isURL = typeAttr.type().equals(UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.URL]);
        return !isURL;
    }
}

public class MblogPrepreprocessor {

    String ifile;

    public MblogPrepreprocessor(String ifile) {
        this.ifile = ifile;
    }

    protected List<String> buildStopwordList(String stopFile) {
        List<String> stopwords = new ArrayList<>();
        String line;

        try (FileReader fr = new FileReader(stopFile);
                BufferedReader br = new BufferedReader(fr)) {
            while ((line = br.readLine()) != null) {
                stopwords.add(line.trim());
            }
            br.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    Analyzer constructAnalyzer() {
        //Analyzer eanalyzer = new StandardAnalyzer();
        Analyzer eanalyzer = new WebDocAnalyzer(
                StopFilter.makeStopSet(buildStopwordList("stop.txt"))); // default analyzer
        return eanalyzer;
    }

    String analyzeText(String txt, Analyzer analyzer) throws Exception {
        StringBuffer tokenizedContentBuff = new StringBuffer();

        TokenStream stream = analyzer.tokenStream("dummy", new StringReader(txt));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();

        int numwords = 0;
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokenizedContentBuff.append(term).append(" ");
            numwords++;
        }

        stream.end();
        stream.close();

        return tokenizedContentBuff.toString();
    }

    String analyzeLine(String txt, Analyzer analyzer) throws Exception {
        String[] tokens = txt.split("\\t");
        String id = tokens[0];

        StringBuffer tokenizedContentBuff = new StringBuffer();
        tokenizedContentBuff.append(id).append("\t");

        TokenStream stream = analyzer.tokenStream("dummy", new StringReader(tokens[1]));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();

        int numwords = 0;
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokenizedContentBuff.append(term).append(" ");
            numwords++;
        }

        stream.end();
        stream.close();

        return tokenizedContentBuff.toString();
    }

    static Date getTwitterDate(String date) throws Exception {
        final String TWITTER = "EEE MMM dd HH:mm:ss ZZZZZ yyyy";
        SimpleDateFormat sf = new SimpleDateFormat(TWITTER, Locale.ENGLISH);
        sf.setLenient(true);
        return sf.parse(date);
    }

    // Generate a string of the following format
    // <id> <timestamp> <text>
    String processJSON(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        String id = obj.getString("id_str");
        String text = new String(obj.getString("text"));
        String timestamp = obj.getString("created_at");

        Date date = getTwitterDate(timestamp);
        long epoch = date.getTime();

        StringBuffer buff = new StringBuffer();
        buff
                .append(id)
                .append("\t")
                .append(epoch)
                .append("\t")
                .append(text);

        return buff.toString();
    }

    void proces() throws Exception {
        String text = null;
        String[] tokens = null;

        FileReader fr = new FileReader(ifile);
        BufferedReader br = new BufferedReader(fr);
        FileWriter fw = new FileWriter(ifile + ".analyzed");
        BufferedWriter bw = new BufferedWriter(fw);

        Analyzer analyzer = constructAnalyzer();
        boolean jsonInput = ifile.endsWith(".json");

        String line;
        while ((line = br.readLine()) != null) {
            if (jsonInput) {
                line = processJSON(line);
                tokens = line.split("\\t");
                text = tokens[tokens.length - 1];
            }

            String ppline = !jsonInput ? analyzeLine(line, analyzer) : analyzeText(text, analyzer);
            if (ppline != null) {
                if (jsonInput) {
                    ppline = tokens[0] + "\t" + tokens[1] + "\t" + ppline;
                }

                bw.write(ppline);
                bw.newLine();
            }
        }
        bw.close();
        fw.close();
        br.close();
        fr.close();
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("usage: java MblogPreprocessor <input file>");
                return;
            }
            MblogPrepreprocessor pp = new MblogPrepreprocessor(args[0]);
            pp.proces();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
