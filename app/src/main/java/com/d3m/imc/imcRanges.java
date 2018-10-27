package com.d3m.imc;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Iterator;
import android.content.Context;

public class imcRanges {
    public class imcRange {
        private double threshold; // the maximum imc for this range
        private int color;		// getting this color when displayed
        String label;			// naming this imc range
        imcRange(double d, int aColor, String aLabel){
            color = aColor; threshold = d; label = aLabel;
        }
        int getColor(){
            return color;
        }
        double getThreshold(){
            return threshold;
        }
        public String getLabel() {
            return label;
        }
    }

    private imcRange neutral = null;
    private ArrayList<imcRange> imcRangeList = new ArrayList<imcRange>();
    private Context applicationContext;

    // Fake unused public constructor for signed application build
    public imcRanges() {

    }

    public imcRanges(Context theApplicationContext){
        applicationContext = theApplicationContext;
        imcRangeList.add(new imcRange(16.5,android.graphics.Color.RED, applicationContext.getString(R.string.famelique)));
        imcRangeList.add(new imcRange(18.5,android.graphics.Color.MAGENTA, applicationContext.getString(R.string.maigre)));
        imcRangeList.add(new imcRange(25,android.graphics.Color.GREEN, applicationContext.getString(R.string.bien)));
        imcRangeList.add(new imcRange(30,android.graphics.Color.CYAN, applicationContext.getString(R.string.rond)));
        imcRangeList.add(new imcRange(35,android.graphics.Color.YELLOW, applicationContext.getString(R.string.gros)));
        imcRangeList.add(new imcRange(40,android.graphics.Color.MAGENTA, applicationContext.getString(R.string.obese)));
        imcRangeList.add(new imcRange(Double.MAX_VALUE,android.graphics.Color.RED, applicationContext.getString(R.string.morbide)));
        neutral = new imcRange(0,android.graphics.Color.WHITE, applicationContext.getString(R.string.neutre));
    }

    // get the imc range for a given imc, ie the closest one greater than this imc
    public imcRange getImcRange(double imc){
        imcRange theImcRange = neutral;
        Iterator<imcRange> itr = imcRangeList.iterator();
        while(itr.hasNext()){
            theImcRange = itr.next();
            if(imc < theImcRange.getThreshold())
                break;
        }
        return theImcRange;
    }

    public String[] getImcRangeLabels(){
        int i = imcRangeList.size();
        String[] result = new String[i];
        while(i-- > 0)
            result[i] = imcRangeList.get(i).getLabel();
        return result;
    }
    public String getImcRangeLabel(int i){
        if(i < imcRangeList.size()-1)
            return applicationContext.getString(R.string.upto) + " " + imcRangeList.get(i).getThreshold() + ": " + imcRangeList.get(i).getLabel();
        else
            return applicationContext.getString(R.string.beyond) + ": " + imcRangeList.get(i).getLabel();
    }
    public int getImcRangeColor(int i){
        return imcRangeList.get(i).getColor();
    }
}
