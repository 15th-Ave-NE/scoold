/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.erudika.scoold.core;

import com.erudika.para.core.Tag;
import com.erudika.para.core.PObject;
import com.erudika.para.annotations.Stored;
import com.erudika.para.core.Linkable;
import com.erudika.para.core.ParaObject;
import com.erudika.para.utils.Config;
import com.erudika.para.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.click.control.Form;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 *
 * @author Alex Bogdanovski <albogdano@me.com>
 */
public abstract class Post extends PObject{
	private static final long serialVersionUID = 1L;

	@Stored private String body;
	@Stored private String title;
	@Stored private Long viewcount;
	@Stored private String answerid;
	@Stored private String revisionid;
	@Stored private String closerid;
	@Stored private String tags;
	@Stored private Long answercount;
	@Stored private Long lastactivity;
	@Stored private Boolean deleteme;
	@Stored private String lasteditby;
	@Stored private Long commentcount;
	@Stored private String deletereportid;
	@Stored private Integer oldvotes;
	@Stored private String oldtags;

	private transient User author;
	private transient User lastEditor;
	private transient ArrayList<Comment> comments;
	private transient Form editForm;
	private transient Long pagenum;
	
	public Post(){
		this.answercount = 0L;
		this.viewcount = 0L;
		this.commentcount = 0L;
		this.pagenum = 0L;
		this.deleteme = false;
	}

	public Long getPagenum() {
		return pagenum;
	}

	public void setPagenum(Long pagenum) {
		this.pagenum = pagenum;
	}
	
	public String getOldtags() {
		return oldtags;
	}

	public void setOldtags(String oldtags) {
		this.oldtags = oldtags;
	}
	
	public String getDeletereportid() {
		return deletereportid;
	}

	public void setDeletereportid(String deletereportid) {
		this.deletereportid = deletereportid;
	}

	public Form getEditForm() {
		return editForm;
	}

	public void setEditForm(Form editForm) {
		this.editForm = editForm;
	}

	public String getLasteditby() {
		return lasteditby;
	}

	public void setLasteditby(String lasteditby) {
		this.lasteditby = lasteditby;
	}

	public Boolean getDeleteme() {
		return deleteme;
	}

	public void setDeleteme(Boolean deleteme) {
		this.deleteme = deleteme;
	}

	public Long getLastactivity() {
		return lastactivity;
	}

	public void setLastactivity(Long lastactivity) {
		this.lastactivity = lastactivity;
	}

	public Long getAnswercount() {
		return answercount;
	}

	public void setAnswercount(Long answercount) {
		this.answercount = answercount;
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		setOldtags(this.tags);
		this.tags = Utils.fixCSV(tags);
	}

	public String getCloserid() {
		return closerid;
	}

	public void setCloserid(String closed) {
		this.closerid = closed;
	}

	public String getRevisionid() {
		return revisionid;
	}

	public void setRevisionid(String revisionid) {
		this.revisionid = revisionid;
	}

	public String getAnswerid() {
		return answerid;
	}

	public void setAnswerid(String answerid) {
		this.answerid = answerid;
	}

	public Long getViewcount() {
		return viewcount;
	}

	public void setViewcount(Long viewcount) {
		this.viewcount = viewcount;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		if(StringUtils.trimToNull(title) == null) return;
		title = title.replaceAll("\\p{S}", "");
		title = title.replaceAll("\\p{C}", "");
		this.title = title;
	}

	public String getName() {
		return getTitle();
	}

	public void setName(String name) {
		setTitle(name);
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
	
	public boolean isClosed(){
		return this.closerid != null;
	}

	public String getTagsString(){
		if(StringUtils.isBlank(tags)) return "";
		return tags.substring(1, tags.length() - 1).replaceAll(",", ", ");
	}

	public String create() {
		if(getRevisionid() == null && canHaveRevisions()){
			//create initial revision = simply create a copy
			setRevisionid(Revision.createRevisionFromPost(this, true).create());
		}
		createUpdateOrDeleteTags(getTags(), null);
		return super.create();
	}

	public void update() {
		updateLastActivity();
		if(canHaveRevisions()){
			setRevisionid(Revision.createRevisionFromPost(this, true).create());
		}
		createUpdateOrDeleteTags(getTags(), getOldtags());
		super.update();
	}

	public void delete() {
		// delete post
		super.delete();
		ArrayList<ParaObject> children = new ArrayList<ParaObject>();
		getAllChildren(this, children);
		
		if(canHaveChildren()){
			for (ParaObject reply : getChildren(Reply.class)) {
				// delete Replies
				getAllChildren((Linkable) reply, children);
			}
		}
		getDao().deleteAll(children);

		createUpdateOrDeleteTags(null, getTags());
	}
	
	private void getAllChildren(Linkable p, ArrayList<ParaObject> all){
		// delete Comments
		all.addAll(p.getChildren(Comment.class));
		// delete Revisions
		all.addAll(p.getChildren(Revision.class));
	}

	private void createUpdateOrDeleteTags(String newTags, String oldTags){
		Map<String, Integer> newTagIndex = new HashMap<String, Integer>();
		//parse
		if(newTags != null){
			for (String ntag : newTags.split(",")) {
				newTagIndex.put(ntag.trim(), 1);
			}
		}
		
		//organize
		if(oldTags != null){
			for (String otag : oldTags.split(",")) {
				otag = otag.trim();
				if(newTagIndex.containsKey(otag)){
					newTagIndex.remove(otag);	// no change so remove
				}else{
					newTagIndex.put(otag, -1); // tag missing so deleteRow and update count
				}
			}
		}
		//clean up the empty tag
		newTagIndex.remove("");

		//create update or deleteRow a given tag
		for (Map.Entry<String, Integer> tagEntry : newTagIndex.entrySet()) {
			Tag tag = getDao().read(Tag.id(tagEntry.getKey()));
			switch(tagEntry.getValue()){
				case 1:
					if(tag == null){
						//create tag
						tag = new Tag(tagEntry.getKey());
						getDao().create(tag);
					}else{
						//update tag count
						tag.incrementCount();
						getDao().update(tag);
					}
					break;
				case -1:
					if(tag != null){
						if(tag.getCount() - 1 == 0){
							// delete tag
							getDao().delete(tag);
						}else{
							//update tag count
							tag.decrementCount();
							getDao().update(tag);
						}
					}
					break;
				default: break;
			}
		}
	}

	public static void readAllCommentsForPosts (ArrayList<Post> list, int maxPerPage) {
		for (Post post : list) {
			MutableLong page = new MutableLong(post.getPagenum());
			ArrayList<Comment> commentz = post.getChildren(Comment.class, page, null, null, 5);
			post.setComments(commentz);
			post.setPagenum(page.longValue());
		}
	}
	
	public User getAuthor(){
		if(getCreatorid() == null) return null;
		if(author == null) author = getDao().read(getCreatorid());
		return author;
	}

	public User getLastEditor(){
		if(lasteditby == null) return null;
		if(lastEditor == null) lastEditor = getDao().read(lasteditby);
		return lastEditor;
	}

	public void restoreRevisionAndUpdate(String revisionid){
		Revision rev = getDao().read(revisionid);
		if(rev != null){
			//copy rev data to post
			setTitle(rev.getTitle());
			setBody(rev.getBody());
			setTags(rev.getTags());

			updateLastActivity();
			setRevisionid(rev.getId());
			//update post
			update();
		}
	}

	public void setComments(ArrayList<Comment> comments) {
		this.comments = comments;
	}

	public ArrayList<Comment> getComments(){
		return this.comments;
	}

	public ArrayList<Comment> getComments(MutableLong page) {
		this.comments = getChildren(Comment.class, page, new MutableLong(commentcount), null, Config.MAX_ITEMS_PER_PAGE);
		this.pagenum = page.longValue();
		return this.comments;
	}

	public Long getCommentcount() {
		return this.commentcount;
	}

	public void setCommentcount(Long count){
		this.commentcount = count;
	}

	public ArrayList<Post> getAnswers(String sortby, MutableLong page, MutableLong itemcount){
		return getChildren(Reply.class, page, itemcount, sortby, Config.MAX_ITEMS_PER_PAGE);
	}

	public ArrayList<Revision> getRevisions(MutableLong page, MutableLong itemcount){
		return getChildren(Revision.class, page, itemcount, null, Config.MAX_ITEMS_PER_PAGE);
	}

	public boolean isReply(){
		return this instanceof Reply;
	}

	public boolean isQuestion(){
		return this instanceof Question;
	}

	public boolean isBlackboard(){
		return this instanceof Blackboard;
	}

	public boolean isFeedback(){
		return this instanceof Feedback;
	}

	public boolean isGrouppost(){
		return this instanceof Grouppost;
	}

	public void updateLastActivity(){
		setLastactivity(System.currentTimeMillis());
	}
		
	public String getPostLink(boolean plural, boolean noid, String questionslink, String questionlink, String feedbacklink, 
			String grouplink, String grouppostlink, String classeslink, String classlink){
		Post p = this;
		if(p == null) return "/";
		String ptitle = Utils.spacesToDashes(p.getTitle());
		String pid = (noid ? "" : "/"+p.getId()+"/"+ ptitle);
		if (p.isQuestion()) {
			return plural ? questionslink : questionlink + pid;
		} else if(p.isFeedback()) {
			return plural ? feedbacklink : feedbacklink + pid;
		} else if(p.isGrouppost()){
			return plural ? grouplink+"/"+p.getParentid() : grouppostlink + pid;
		} else if(p.isReply()){
			Post parentp = getDao().read(p.getParentid());
			if(parentp != null){
				return parentp.getPostLink(plural, noid, questionslink, questionlink, 
						feedbacklink, grouplink, grouppostlink, classeslink, classlink);
			}
		}else if(p.isBlackboard()){
			return plural ? classeslink : classlink + (noid ? "" : "/" + p.getParentid());
		}
		return "";
	}

	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Post other = (Post) obj;
		if ((this.body == null) ? (other.body != null) : !this.body.equals(other.body)) {
			return false;
		}
		if ((this.title == null) ? (other.title != null) : !this.title.equals(other.title)) {
			return false;
		}
		if ((this.tags == null) ? (other.tags != null) : !this.tags.equals(other.tags)) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		int hash = 7;
		hash = 97 * hash + (this.body != null ? this.body.hashCode() : 0);
		return hash;
	}
	
	public abstract boolean canHaveChildren();
	
	public abstract boolean canHaveRevisions();
}
