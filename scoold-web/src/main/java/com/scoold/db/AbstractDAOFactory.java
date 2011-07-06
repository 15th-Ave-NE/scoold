package com.scoold.db;

import com.scoold.core.ScooldObject;
import com.scoold.db.cassandra.CasDAOFactory;

public abstract class AbstractDAOFactory {

	public static final String DEFAULT_ENCODING = "UTF-8";
	// GLOBAL LIMITS
	public static final int MAX_COLUMNS = 1024;
	public static final int VOTE_LOCKED_FOR_SEC = 4*7*24*60*60; //1 month in seconds
	public static final long VOTE_LOCK_AFTER = 2*60*1000; //2 minutes in ms
	public static final int MAX_ITEMS_PER_PAGE = 30;
	public static final int MAX_CONTACTS_PER_USER = 2000;
	public static final int MAX_SCHOOLS_PER_USER = MAX_ITEMS_PER_PAGE;
	public static final int MAX_CLASSES_PER_USER = MAX_ITEMS_PER_PAGE;
	public static final int MAX_PHOTOS_PER_UUID = 3000;
	public static final int MAX_DRAWER_ITEMS_PER_UUID = 2000;
	public static final int MAX_COMMENTS_PER_UUID = MAX_COLUMNS;
	public static final int MAX_TEXT_LENGTH = 20000;
	public static final int MAX_TEXT_LENGTH_SHORT = 5000;
	public static final int MAX_MESSAGES_PER_USER = 5000;
	public static final int MAX_MULTIPLE_RECIPIENTS = 50;
	public static final int MAX_TAGS_PER_POST = 5;
	public static final int MAX_ANSWERS_PER_POST = 500;
	public static final int MAX_LABELS_PER_MEDIA = 5;
	public static final int MAX_CONTACT_DETAILS = 15;
	public static final int MAX_IDENTIFIERS_PER_USER = 2;
	public static final int MAX_FAV_TAGS = 50;
	public static final int MAX_INVITES = 50;

	public static final int	DEFAULT_LIMIT = Integer.MAX_VALUE;

	public static enum FactoryType {
//		MYSQL,
		CASSANDRA
	}

    public static AbstractDAOFactory getDAOFactory (FactoryType type) {
        switch(type){
//            case MYSQL: return new MyDAOFactory();
            case CASSANDRA: return new CasDAOFactory(); 
            default: return getDefaultDAOFactory();
        }
    }

    public static AbstractDAOFactory getDefaultDAOFactory(){
//        return getDAOFactory(FactoryType.MYSQL);
        return getDAOFactory(FactoryType.CASSANDRA);
    }

	public abstract AbstractDAOUtils getDAOUtils();

//	public abstract <T extends ScooldObject, PK> GenericDAO<T, PK> getDAO(Class<T> clazz);
	public abstract <T extends ScooldObject> GenericDAO<?, ?> getDAO(Class<T> clazz);
}

