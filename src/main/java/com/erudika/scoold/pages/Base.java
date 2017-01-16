/*
 * Copyright 2013-2017 Erudika. https://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.scoold.pages;

import com.erudika.para.client.ParaClient;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.erudika.para.core.User;
import com.erudika.para.core.utils.ParaObjectUtils;
import com.erudika.para.i18n.CurrencyUtils;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Pager;
import com.erudika.para.utils.Utils;
import com.erudika.scoold.core.Comment;
import com.erudika.scoold.core.Feedback.FeedbackType;
import com.erudika.scoold.core.Language;
import com.erudika.scoold.core.Post;
import com.erudika.scoold.core.Report;
import com.erudika.scoold.core.Report.ReportType;
import com.erudika.scoold.core.ScooldUser;
import com.erudika.scoold.core.ScooldUser.Badge;
import com.erudika.scoold.utils.AppConfig;
import com.erudika.scoold.utils.LanguageUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.click.Page;
import org.apache.click.control.Field;
import org.apache.click.control.Form;
import org.apache.click.control.Radio;
import org.apache.click.control.RadioGroup;
import org.apache.click.control.Submit;
import org.apache.click.control.TextArea;
import org.apache.click.control.TextField;
import org.apache.click.util.ClickUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Base extends Page {
	private static final long serialVersionUID = 1L;
	public static final Logger logger = LoggerFactory.getLogger(Base.class);

	public static final String APPNAME = Config.APP_NAME; //app name
	public static final String CDN_URL = "http://static.scoold.com";
	public static final boolean USE_SESSIONS = false;
	public static final boolean IN_PRODUCTION = Config.IN_PRODUCTION;
	public static final boolean IN_DEVELOPMENT = !IN_PRODUCTION;
	public static final int MAX_ITEMS_PER_PAGE = Config.MAX_ITEMS_PER_PAGE;
	public static final long SESSION_TIMEOUT_SEC = Config.SESSION_TIMEOUT_SEC;
	public static final long ONE_YEAR = 365 * 24 * 60 * 60 * 1000;

	public static final String FEED_KEY_SALT = ":scoold";
	public static final String FB_APP_ID = Config.FB_APP_ID;

	public final String prefix = getContext().getServletContext().getContextPath()+"/";
	public final String localeCookieName = Config.APP_NAME_NS + "-locale";
	public final String countryCookieName = Config.APP_NAME_NS + "-country";
	public final String peoplelink = prefix + "people";
	public final String profilelink = prefix + "profile";

	public String minsuffix = "-min";
	public String imageslink = prefix + "images";
	public String scriptslink = prefix + "scripts";
	public String styleslink = prefix + "styles";

	public final String searchlink = prefix + "search";
	public final String searchquestionslink = searchlink + "/questions";
	public final String searchfeedbacklink = searchlink + "/feedback";
	public final String searchpeoplelink = searchlink + "/people";
	public final String signinlink = prefix + "signin";
	public final String signoutlink = prefix + "signout";
	public final String aboutlink = prefix + "about";
	public final String privacylink = prefix + "privacy";
	public final String termslink = prefix + "terms";
	public final String tagslink = prefix + "tags";
	public final String settingslink = prefix + "settings";
	public final String translatelink = prefix + "translate";
	public final String changepasslink = prefix + "changepass";
	public final String activationlink = prefix + "activation";
	public final String reportslink = prefix + "reports";
	public final String adminlink = prefix + "admin";
	public final String votedownlink = prefix + "votedown";
	public final String voteuplink = prefix + "voteup";
	public final String questionlink = prefix + "question";
	public final String questionslink = prefix + "questions";
	public final String commentlink = prefix + "comment";
	public final String postlink = prefix + "post";
	public final String feedbacklink = prefix + "feedback";
	public final String languageslink = prefix + "languages";
	public String HOMEPAGE = "/";

	public String infoStripMsg = "";
	public boolean authenticated;
	public boolean canComment;
	public ScooldUser authUser;
	public List<Comment> commentslist;
	public List<String> badgelist;
	public Pager itemcount;
	public String showParam;

	public transient Utils utils = Utils.getInstance();
	public transient CurrencyUtils currutils = CurrencyUtils.getInstance();
	public transient HttpServletRequest req = getContext().getRequest();
	public transient HttpServletResponse resp = getContext().getResponse();
	public static Map<String, String> deflang = Language.ENGLISH;
	public Map<String, String> lang = deflang;
	public Locale currentLocale;

	public transient ParaClient pc = AppConfig.client();
	public transient LanguageUtils langutils = new LanguageUtils();

	public Base() {
		commentslist = new ArrayList<Comment>();
		badgelist = new ArrayList<String>();
		itemcount = new Pager();
		checkAuth();
		cdnSwitch();
		showParam = getParamValue("show");
		canComment = authenticated && (authUser.hasBadge(Badge.ENTHUSIAST) || authUser.isModerator());
		addModel("userip", req.getRemoteAddr());
		addModel("isAjaxRequest", isAjaxRequest());
		addModel("reportTypes", ReportType.values());
		addModel("returnto", req.getAttribute("javax.servlet.forward.request_uri"));
		addModel("navbarFixedClass", Config.getConfigBoolean("fixed_nav", false) ? "navbar-fixed" : "none");
		addModel("showBranding", Config.getConfigBoolean("show_branding", true));
		addModel("logoUrl", Config.getConfigParam("logo_url", imageslink + "/logo.png"));
		addModel("logoWidth", Config.getConfigInt("logo_width", 90));
		addModel("bgcolor1", Config.getConfigParam("background_color1", "#03a9f4"));
		addModel("bgcolor2", Config.getConfigParam("background_color2", "#0277bd"));
		addModel("bgcolor3", Config.getConfigParam("background_color3", "#e0e0e0"));
		addModel("txtcolor1", Config.getConfigParam("text_color1", "#039be5"));
		addModel("txtcolor2", Config.getConfigParam("text_color2", "#ec407a"));
		addModel("txtcolor3", Config.getConfigParam("text_color3", "#444444"));

	}

	public void onInit() {
		super.onInit(); //To change body of generated methods, choose Tools | Templates.
		initLanguage();
	}

	/* * PRIVATE METHODS * */

	private void cdnSwitch() {
		if (IN_PRODUCTION) {
			scriptslink = CDN_URL;
			imageslink = CDN_URL;
			styleslink = CDN_URL;
			minsuffix = "-min";
		} else {
			scriptslink = prefix + "scripts";
			imageslink = prefix + "images";
			styleslink = prefix + "styles";
			minsuffix = "";
		}
	}

	private void checkAuth() {
		try{
			if (getStateParam(Config.AUTH_COOKIE) != null) {
				User u = pc.me(getStateParam(Config.AUTH_COOKIE));
				if (u != null) {
					HOMEPAGE = profilelink;
					authUser = pc.read(ScooldUser.id(u.getId()));
					if (authUser == null) {
						authUser = new ScooldUser(u.getId(), u.getEmail(), u.getName());
						authUser.setAppid(u.getAppid());
						authUser.setCreatorid(u.getId());
						authUser.setTimestamp(u.getTimestamp());
						authUser.setLastseen(u.getUpdated());
						authUser.setGroups(u.getGroups());
						authUser.setIdentifier(u.getIdentifier());
						authUser.setPicture(u.getPicture());
						authUser.create();
					}
					authUser.setGroups(u.getGroups());
					authUser.setPicture(u.getPicture());
					authUser.setUser(u);
					Utils.removeStateParam("intro", req, resp);
					infoStripMsg = "";
					authenticated = true;
					if (!StringUtils.isBlank(authUser.getNewbadges())) {
						badgelist.addAll(Arrays.asList(authUser.getNewbadges().split(",")));
						authUser.setNewbadges("none");
					}
				}
			} else {
				String intro = getStateParam("intro");
				infoStripMsg = "intro";
				if ("0".equals(intro)) {
					infoStripMsg = "";
				} else {
					Utils.setStateParam("intro", "1", req, resp);
				}
				authenticated = false;
			}
		} catch (Exception e) {
			authenticated = false;
			logger.warn("CheckAuth failed for {}: {}", req.getRemoteUser(), e);
			clearSession();
			if (!req.getRequestURI().startsWith("/index.htm"))
				setRedirect(HOMEPAGE);
		}
	}

	private void initLanguage() {
		langutils.setDefaultLanguage(Language.ENGLISH);
		String cookieLoc = ClickUtils.getCookieValue(req, localeCookieName);
		Locale requestLocale = langutils.getProperLocale(req.getLocale().getLanguage());
		String langFromLocation = getLanguageFromLocation();
		String langname = (cookieLoc != null) ? cookieLoc : (langFromLocation != null) ?
				langFromLocation : requestLocale.getLanguage();
		//locale cookie set?
		setCurrentLocale(langname, false);
	}

	/* * PUBLIC METHODS * */

	public final void setCurrentLocale(String langname, boolean setCookie) {
		currentLocale = langutils.getProperLocale(langname);
		lang = langutils.readLanguage(Config.APP_NAME_NS, currentLocale.getLanguage());

		if (setCookie) {
			//create a cookie
			int maxAge = 5 * 60 * 60 * 24 * 365;  //5 years
			ClickUtils.setCookie(req, resp, localeCookieName, currentLocale.getLanguage(), maxAge, "/");
		}
	}

	private String getLanguageFromLocation() {
		String language = null;
		try {
			String country = ClickUtils.getCookieValue(req, countryCookieName);
			if (country != null) {
				Locale loc = currutils.getLocaleForCountry(country.toUpperCase());
				if (loc != null) {
					language = loc.getLanguage();
				}
			}
		} catch(Exception exc) {}

		return language;
    }

	public final boolean isAjaxRequest() {
		//context.isAjaxRequest()
		return getContext().isAjaxRequest();
	}

	/* COMMENTS */

	public final void processNewCommentRequest(Sysprop parent) {
		if (param("deletecomment") && authenticated) {
			String id = getParamValue("deletecomment");
			Comment c = pc.read(id);
			if (c != null && (c.getCreatorid().equals(authUser.getId()) || inRole("mod"))) {
				// check parent and correct (for multi-parent-object pages)
				if (parent == null || !c.getParentid().equals(parent.getId())) {
					parent = pc.read(c.getParentid());
				}
				c.delete();
				if (!inRole("mod")) {
					addBadge(Badge.DISCIPLINED, true);
				}
				if (parent != null) {
					try {
//						Long count = (Long) PropertyUtils.getProperty(parent, "commentcount");
//						pc.putColumn(parent.getId(), "commentcount", Long.toString(count - 1));
						parent.addProperty("commentcount", Long.toString(((Long) parent.getProperty("commentcount")) - 1));
						parent.update();
					} catch (Exception ex) {
						logger.error(null, ex);
					}
				}
			}
		} else if (canComment && param("comment") && parent != null) {
			String comment = getParamValue("comment");
			String parentid = parent.getId();
			if (StringUtils.isBlank(comment)) return;
			Comment lastComment = new Comment();
			lastComment.setComment(comment);
			lastComment.setParentid(parentid);
			lastComment.setCreatorid(authUser.getId());
			lastComment.setAuthor(authUser.getName());

			if (lastComment.create() != null) {
				long commentCount = authUser.getComments();
				addBadgeOnce(Badge.COMMENTATOR, commentCount >= AppConfig.COMMENTATOR_IFHAS);
				authUser.setComments(commentCount + 1);
				authUser.update();
				commentslist.add(lastComment);
				addModel("newcomment", lastComment);

				try{
//					Long count = (Long) PropertyUtils.getProperty(parent, "commentcount");
//					pc.putColumn(parent.getId(), "commentcount", Long.toString(count + 1));
					parent.addProperty("commentcount", Long.toString(((Long) parent.getProperty("commentcount")) + 1));
					parent.update();
				} catch (Exception ex) {
					logger.error(null, ex);
				}
			}
		}
	}

	/****  POSTS  ****/

	public final <T extends Sysprop> Form getQuestionForm() {
		return getPostForm(Question.class, "qForm", "ask-question-form");
	}

	public final Form getAnswerForm() {
		Form aForm = new Form("aForm");
		aForm.setId("answer-question-form");

		TextArea body = new TextArea("body", lang.get("posts.answer"), true);
		body.setMinLength(15);
		body.setRows(4);
		body.setCols(5);

		TextField hideme = new TextField("additional", false);
		hideme.setLabel("Leave blank!");
		hideme.setAttribute("class", "hide");

        Submit submit = new Submit("answerbtn", lang.get("post"), this, "onAnswerClick");
        submit.setAttribute("class", "btn waves-effect waves-light");
		submit.setId("answer-btn");

		aForm.add(hideme);
        aForm.add(body);
        aForm.add(submit);

		return aForm;
	}

	public final Form getFeedbackForm() {
		return getPostForm(Feedback.class, "fForm", "write-feedback-form");
	}

	public final Form getPostForm(Class<?> type, String name, String id) {
		if (!authenticated) return null;

		Form qForm = new Form(name);
		qForm.setId(id);

        TextField title = new TextField("title", true);
        title.setLabel(lang.get("posts.title"));
        title.setMaxLength(255);
		title.setMinLength(10);
		title.setTabIndex(1);

		TextArea body = new TextArea("body", true);
		body.setLabel(lang.get("messages.text"));
		body.setMinLength(10);
		body.setMaxLength(AppConfig.MAX_TEXT_LENGTH);
		body.setRows(4);
		body.setCols(5);
		body.setTabIndex(2);

		Field tags = null;
		if (type.equals(Question.class)) {
			tags = new TextField("tags", true);
			tags.setLabel(lang.get("tags.title"));
			((TextField) tags).setMaxLength(255);
			tags.setTabIndex(3);
		} else if (type.equals(Feedback.class)) {
			tags = new RadioGroup("tags", lang.get("feedback.type"), true);
			String bug = FeedbackType.BUG.toString();
			String question = FeedbackType.QUESTION.toString();
			String suggestion = FeedbackType.SUGGESTION.toString();

			((RadioGroup) tags).add(new Radio(question, lang.get("feedback." + question)));
			((RadioGroup) tags).add(new Radio(suggestion, lang.get("feedback." + suggestion)));
			((RadioGroup) tags).add(new Radio(bug, lang.get("feedback." + bug)));
			((RadioGroup) tags).setVerticalLayout(false);
		}

		TextField hideme = new TextField("additional", false);
		hideme.setLabel("Leave blank!");
		hideme.setAttribute("class", "hide");

        Submit submit = new Submit("askbtn", lang.get("post"), this, "onAskClick");
        submit.setAttribute("class", "btn waves-effect waves-light");
		submit.setId("ask-btn");

        qForm.add(title);
		qForm.add(hideme);
        qForm.add(body);
		qForm.add(tags);
        qForm.add(submit);

		return qForm;
	}

	public final boolean isValidPostEditForm(Form qForm) {
        String tags = qForm.getFieldValue("tags");
		String additional = qForm.getFieldValue("additional");

		if (!StringUtils.isBlank(additional)) {
			qForm.setError("You are not supposed to do that!");
		}
		if (tags != null && StringUtils.split(tags, ",").length >
				AppConfig.MAX_TAGS_PER_POST) {
			qForm.setError(lang.get("tags.toomany"));
		}

		return qForm.isValid();
	}

	public final boolean isValidQuestionForm(Form qForm) {
        String tags = qForm.getFieldValue("tags");
		String additional = qForm.getFieldValue("additional");

		if (!StringUtils.isBlank(additional)) {
			qForm.setError("You are not supposed to do that!");
		}
		if (StringUtils.split(tags, ",").length > AppConfig.MAX_TAGS_PER_POST) {
			qForm.setError(lang.get("tags.toomany"));
		}

		return qForm.isValid();
	}

	public final boolean isValidAnswerForm(Form aForm, Post showPost) {
		String additional = aForm.getFieldValue("additional");
		if (!StringUtils.isBlank(additional)) {
			aForm.setError("You are not supposed to do that!");
		}
        return aForm.isValid();
	}

	public final void createAndGoToPost(Class<? extends Post> type) {
		String parentid = getParamValue(Config._PARENTID);

		Post newq = ParaObjectUtils.toObject(type.getSimpleName().toLowerCase());
		newq.setTitle(getParamValue("title"));
		newq.setBody(getParamValue("body"));
		newq.setTags(Arrays.asList(StringUtils.split(getParamValue("tags"), ",")));
		if (parentid != null) newq.setParentid(parentid);
		newq.setCreatorid(authUser.getId());
		newq.create();

		setRedirect(getPostLink(newq, false, false));
	}

	/******** VOTING ********/

	public void processVoteRequest(String type, String id) {
		if (id == null) return;
		ParaObject votable = pc.read(id);
		boolean result = false;
		Integer votes = 0;

		if (votable != null && authenticated) {
			try {
				ScooldUser author = pc.read(votable.getCreatorid());
				votes = (Integer) PropertyUtils.getProperty(votable, "votes");

				if (param("voteup")) {
					result = votable.voteUp(authUser.getId());
					if (!result) return;
					votes++;
					authUser.incrementUpvotes();

					int award = 0;

					if (votable instanceof Post) {
						Post p = (Post) votable;
						if (p.isReply()) {
							addBadge(Badge.GOODANSWER, votes >= AppConfig.GOODANSWER_IFHAS);
							award = AppConfig.ANSWER_VOTEUP_REWARD_AUTHOR;
						} else if (p.isQuestion()) {
							addBadge(Badge.GOODQUESTION, votes >= AppConfig.GOODQUESTION_IFHAS);
							award = AppConfig.QUESTION_VOTEUP_REWARD_AUTHOR;
						} else {
							award = AppConfig.VOTEUP_REWARD_AUTHOR;
						}
					} else {
						award = AppConfig.VOTEUP_REWARD_AUTHOR;
					}

					if (author != null) {
						author.addRep(award);
						author.update();
					}

				} else if (param("votedown")) {
					result = votable.voteDown(authUser.getId());
					if (!result) return;
					votes--;
					authUser.incrementDownvotes();

					if (StringUtils.equalsIgnoreCase(type,
							Comment.class.getSimpleName()) && votes <= -5) {
						//treat comment as offensive or spam - hide
						((Comment) votable).setHidden(true);
					} else if (StringUtils.equalsIgnoreCase(type,
							Post.class.getSimpleName()) && votes <= -5) {
						Post p = (Post) votable;

						//mark post for closing
						Report rep = new Report();
						rep.setParentid(id);
						rep.setLink(getPostLink(p, false, false));
						rep.setDescription(lang.get("posts.forclosing"));
						rep.setSubType(ReportType.OTHER);
						rep.setAuthor("System");

						rep.create();
					}

					if (author != null) {
						author.removeRep(AppConfig.POST_VOTEDOWN_PENALTY_AUTHOR);
						author.update();
						//small penalty to voter
						authUser.removeRep(AppConfig.POST_VOTEDOWN_PENALTY_VOTER);
					}
				}
			} catch (Exception ex) {
				logger.error(null, ex);
			}

			addBadgeOnce(Badge.SUPPORTER, authUser.getUpvotes() >= AppConfig.SUPPORTER_IFHAS);
			addBadgeOnce(Badge.CRITIC, authUser.getDownvotes() >= AppConfig.CRITIC_IFHAS);
			addBadgeOnce(Badge.VOTER, authUser.getTotalVotes() >= AppConfig.VOTER_IFHAS);

			if (result) {
				votable.setVotes(votes);
				votable.update();
//				pc.putColumn(votable.getId(), "votes", votes.toString());
			}
		}

		addModel("voteresult", result);
	}

	/******  MISC *******/

	public final boolean param(String param) {
		return getContext().getRequestParameter(param) != null;
	}

	public final String getParamValue(String param) {
		return getContext().getRequestParameter(param);
	}

	public String getPostLink(Post p, boolean plural, boolean noid) {
		return p.getPostLink(plural, noid, questionslink, questionlink, feedbacklink);
	}

	public final int getIndexInBounds(int index, int count) {
		if (index >= count) index = count - 1;
		if (index < 0) index = 0;
		return index;
	}

	public final void setStateParam(String name, String value) {
		Utils.setStateParam(name, value, req, resp,
				USE_SESSIONS);
	}

	public final String getStateParam(String name) {
		return Utils.getStateParam(name, req);
	}

	public final void removeStateParam(String name) {
		Utils.removeStateParam(name, req, resp);
	}

	public final void clearSession() {
		if (req != null) {
			HttpSession session = req.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			removeStateParam(Config.AUTH_COOKIE);
		}
	}

	public boolean inRole(String role) {
		return req.isUserInRole(role);
	}

	public final boolean addBadgeOnce(Badge b, boolean condition) {
		return addBadge(b, condition && !authUser.hasBadge(b));
	}

	public final boolean addBadge(Badge b, boolean condition) {
		return addBadge(b, null, condition);
	}

	public final boolean addBadge(Badge b, ScooldUser u, boolean condition) {
		if (u == null) u = authUser;
		if (!authenticated || !condition) return false;

		String newb = StringUtils.isBlank(u.getNewbadges()) ? "" : u.getNewbadges().concat(",");
		newb = newb.concat(b.toString());

		u.addBadge(b);
		u.setNewbadges(newb);
		u.update();

		return true;
	}

	public final boolean removeBadge(Badge b, ScooldUser u, boolean condition) {
		if (u == null) u = authUser;
		if (!authenticated || !condition) return false;

		if (StringUtils.contains(u.getNewbadges(), b.toString())) {
			String newb = u.getNewbadges();
			newb = newb.replaceAll(b.toString().concat(","), "");
			newb = newb.replaceAll(b.toString(), "");
			newb = newb.replaceFirst(",$", "");
			u.setNewbadges(newb);
		}

		u.removeBadge(b);
		u.update();

		return true;
	}

	public void onDestroy() {
		if (authenticated && !isAjaxRequest()) {
			long oneYear = authUser.getTimestamp() + ONE_YEAR;

			addBadgeOnce(Badge.ENTHUSIAST, authUser.getVotes() >= AppConfig.ENTHUSIAST_IFHAS);
			addBadgeOnce(Badge.FRESHMAN, authUser.getVotes() >= AppConfig.FRESHMAN_IFHAS);
			addBadgeOnce(Badge.SCHOLAR, authUser.getVotes() >= AppConfig.SCHOLAR_IFHAS);
			addBadgeOnce(Badge.TEACHER, authUser.getVotes() >= AppConfig.TEACHER_IFHAS);
			addBadgeOnce(Badge.PROFESSOR, authUser.getVotes() >= AppConfig.PROFESSOR_IFHAS);
			addBadgeOnce(Badge.GEEK, authUser.getVotes() >= AppConfig.GEEK_IFHAS);
			addBadgeOnce(Badge.SENIOR, System.currentTimeMillis() >= oneYear);

			if (!StringUtils.isBlank(authUser.getNewbadges())) {
				if (authUser.getNewbadges().equals("none")) {
					authUser.setNewbadges(null);
				}
				authUser.update();
			}
		}
	}

	public static final String DESCRIPTION = "Scoold is friendly place where you can get answers to your questions.";
	public static final String KEYWORDS = "scoold, knowledge sharing, collaboration, wiki, forum, Q&A, questions and answers";

	public String getTemplate() {
		return "base.htm";
	}
}
