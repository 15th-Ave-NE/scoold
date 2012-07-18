/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.scoold.pages.click;

import com.scoold.core.Language;
import com.scoold.db.AbstractDAOFactory;
import com.scoold.db.AbstractDAOUtils;
import com.scoold.pages.BasePage;
import java.util.Locale;
import java.util.Map;
import org.apache.click.util.ErrorPage;

/**
 *
 * @author alexb
 */
public class Error extends ErrorPage {

    public String title;
	public Map<String, String> lang;
	public AbstractDAOUtils daoutils;

    public Error() {
		daoutils = AbstractDAOFactory.getDefaultDAOFactory().getDAOUtils();
		Locale loc = Language.getProperLocale(getContext().getRequest().getLocale().getLanguage());
		lang = Language.readLanguage(loc);
        title = lang.get("error.title");
		
		addModel("APPNAME", BasePage.APPNAME);
		addModel("DESCRIPTION", BasePage.DESCRIPTION);
		addModel("KEYWORDS", BasePage.KEYWORDS);
		addModel("CDN_URL", BasePage.CDN_URL);
		addModel("IN_PRODUCTION", BasePage.IN_PRODUCTION);
    }

	public String getTemplate() {
		return "click/error.htm";
	}


} 
