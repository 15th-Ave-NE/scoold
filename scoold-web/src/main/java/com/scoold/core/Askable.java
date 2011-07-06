/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.scoold.core;

import java.util.ArrayList;
import org.apache.commons.lang.mutable.MutableLong;

/**
 *
 * @author alexb
 */
public interface Askable<T> {
	public ArrayList<T> getQuestions(String sortBy, MutableLong pagenum, MutableLong itemcount);
}
