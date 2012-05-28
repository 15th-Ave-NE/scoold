/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.scoold.pages;

import com.scoold.core.Language;
import com.scoold.core.Translation;
import com.scoold.core.User;
import com.scoold.core.User.Badge;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

/**
 *
 * @author alexb
 */
public class Translate extends BasePage{

	public String title;
	
	public Locale showLocale;
	public int showLocaleProgress;
	public Map<String, Integer> translationcountsmap;
	public ArrayList<Translation> translationslist;	// only the translations for given key
	public Map<String, Long> approvedTransMap;
	public static Map<String, String> deflang = Language.getDefaultLanguage();
	public ArrayList<String> langkeys;
	public int showIndex;

	public Translate(){
		title = lang.get("translate.title");
		langkeys = new ArrayList<String>();

		if (param("locale")) {
			showLocale = Language.ALL_LOCALES.get(getParamValue("locale"));
			if(showLocale == null || showLocale.getLanguage().equals("en")){
				setRedirect(languageslink);
				return;
			}
			title += " - " + showLocale.getDisplayName(showLocale);

			approvedTransMap = Translation.getTranslationDao().
					readApprovedIdsForLocale(showLocale.getLanguage());

			for (String key : Language.getDefaultLanguage().keySet()) {
				langkeys.add(key);
			}
			
			if(param("index")){
				showIndex = NumberUtils.toInt(getParamValue("index"), 1) - 1;
				if(showIndex <= 0){
					showIndex = 0;
				}else if(showIndex >= langkeys.size()){
					showIndex = langkeys.size() - 1;
				}
			}
		}else{
			setRedirect(languageslink);
		}
	}
	 
	public void onGet(){
		if(isAjaxRequest()) return;
		if(param("locale")){
			double c1 = (double) approvedTransMap.size();
			double c2 = (double) lang.size();
			showLocaleProgress = (int) (Math.round((c1 / c2) * 100));

			if(param("index")){
				// this is what is currently shown for translation
				String langkey = langkeys.get(showIndex);
				
				translationslist = Translation.getTranslationDao().
								readAllTranslationsForKey(showLocale.getLanguage(),
								langkey, pagenum, itemcount);
			}else{
				translationcountsmap = Translation.getTranslationDao().
						readTranslationCountForAllKeys(showLocale.getLanguage(), langkeys);
			}
		}
	}

	public void onPost() {
		if(param("locale") && param("gettranslationhtmlcode")){
			String value = StringUtils.trim(getParamValue("value"));
			String langkey = langkeys.get(showIndex);
			boolean isTranslated = approvedTransMap.containsKey(langkey);
			if(!StringUtils.isBlank(value) && (!isTranslated || inRole("admin"))){
				Translation trans = new Translation(showLocale.getLanguage(), langkey, value);
				trans.setUserid(authUser.getId());
				trans.create();
				addModel("newtranslation", trans);
			}
			if(!isAjaxRequest()){
				setRedirect(translatelink + "/" + showLocale.getLanguage()+"/"+getNextIndex(showIndex));
			}
		}else if(param("reset") && inRole("admin")){
			String key = getParamValue("reset");
			if(lang.containsKey(key)){
				if(param("global")){
					// global reset: delete all approved translations for this key
					Translation.getTranslationDao().disapproveAllForKey(key);
				}else{
					// loca reset: delete all approved translations for this key and locale
					Translation.getTranslationDao().disapproveAllForKey(key, showLocale.getLanguage());
				}
				if(!isAjaxRequest())
					setRedirect(translatelink+"/"+showLocale.getLanguage());
			}
		}else if(param("approve") && inRole("admin")){
			Long id = NumberUtils.toLong(getParamValue("approve"), 0);
			if(id > 0L){
				Translation trans = Translation.getTranslationDao().read(id);
				User u = User.getUser(trans.getUserid());

				if(approvedTransMap.containsValue(id)){
					trans.disapprove();
					approvedTransMap.remove(trans.getKey());
				}else{
					trans.approve();
					approvedTransMap.put(trans.getKey(), id);
					addBadge(Badge.POLYGLOT, u, true);
				}
			}
			if(!isAjaxRequest())
				setRedirect(translatelink+"/"+showLocale.getLanguage()+"/"+(showIndex+1));
		}else if(param("delete")){
			Long id = NumberUtils.toLong(getParamValue("delete"), 0);
			if(id > 0L){
				Translation t = Translation.getTranslationDao().read(id);
				if(authUser.getId().equals(t.getUserid()) || inRole("admin")){
					t.disapprove();
					t.delete();
				}
				if(!isAjaxRequest())
					setRedirect(translatelink+"/"+showLocale.getLanguage()+"/"+(showIndex+1));
			}
		}
	}
	
	private int getNextIndex(int start){
		if(start < 0) start = 0;
		if(start >= approvedTransMap.size()) start = approvedTransMap.size() - 1;
		int nexti = (start + 1) >= langkeys.size() ? 0 : (start + 1);
		
		// if there are untranslated strings go directly there
		if(approvedTransMap.size() != langkeys.size()){
			while(approvedTransMap.containsKey(langkeys.get(nexti))){
				nexti = (nexti + 1) >= langkeys.size() ? 0 : (nexti + 1);
			}
		}

		return nexti;
	}

}
