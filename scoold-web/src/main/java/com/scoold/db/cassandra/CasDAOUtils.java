/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scoold.db.cassandra;

import com.eaio.uuid.UUID;
import com.scoold.core.Post;
import com.scoold.core.ScooldObject;
import com.scoold.core.Stored;
import com.scoold.core.Votable;
import com.scoold.db.AbstractDAOUtils;
import com.scoold.db.cassandra.CasDAOFactory.CF;
import com.scoold.db.cassandra.CasDAOFactory.Column;
import com.scoold.db.cassandra.CasDAOFactory.SCF;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.prettyprint.cassandra.serializers.SerializerTypeInferer;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.OperationType;
import me.prettyprint.hector.api.ClockResolution;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.ConsistencyLevelPolicy;
import me.prettyprint.hector.api.HConsistencyLevel;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.HCounterColumn;
import me.prettyprint.hector.api.beans.HSuperColumn;
import me.prettyprint.hector.api.beans.Row;
import me.prettyprint.hector.api.beans.SuperRows;
import me.prettyprint.hector.api.beans.SuperSlice;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.CountQuery;
import me.prettyprint.hector.api.query.CounterQuery;
import me.prettyprint.hector.api.query.MultigetSliceQuery;
import me.prettyprint.hector.api.query.SliceQuery;
import me.prettyprint.hector.api.query.SubCountQuery;
import me.prettyprint.hector.api.query.SuperCountQuery;
import me.prettyprint.hector.api.query.SuperSliceQuery;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.mutable.MutableLong;


/**
 *
 * @author alexb
 */
public class CasDAOUtils extends AbstractDAOUtils {
	
	private static final Logger logger = Logger.getLogger(CasDAOUtils.class.getName());
	private static final long TIMER_OFFSET = 1269182927663L;
	private static Keyspace keyspace;
	private Mutator<String> mutator;
//	private CasDAOUtils INSTANCE = new CasDAOUtils();
	private long voteLockAfter;

	static {
		CassandraHostConfigurator config = new CassandraHostConfigurator();
		config.setHosts(CasDAOFactory.CLUSTER_NODE1);
		config.setPort(9160);
		config.setRetryDownedHostsDelayInSeconds(60);
		Cluster cluster = HFactory.getOrCreateCluster(CasDAOFactory.CLUSTER, config);
		keyspace = HFactory.createKeyspace(CasDAOFactory.KEYSPACE, cluster,
			new ConsistencyLevelPolicy() {
				public HConsistencyLevel get(OperationType arg0) {
					return HConsistencyLevel.QUORUM;
				}
				public HConsistencyLevel get(OperationType arg0, String arg1) {
					return HConsistencyLevel.QUORUM;
				}
			});
	}
	
	public CasDAOUtils() {
		mutator = createMutator();
		voteLockAfter = convertMsTimeToCasTime(keyspace, CasDAOFactory.VOTE_LOCK_AFTER);
	}

	public Keyspace getKeyspace(){
		return keyspace;
	}

	/********************************************
	 *			CORE OBJECT CRUD FUNCTIONS
	********************************************/

	public Long create(ScooldObject so, CF<String> cf){
		Long id = create(so, cf, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
		return id;
	}

	public Long create(ScooldObject so, CF<String> cf, Mutator<String> mut){
		return create(null, so, cf, mut);
	}

	public Long create(String key, ScooldObject so, CF<String> cf, Mutator<String> mut){
		if(so == null || cf == null || StringUtils.isBlank(cf.getName())) return null;
		if(StringUtils.isBlank(so.getUuid())) so.setUuid(new UUID().toString());
		if(so.getId() == null) so.setId(getNewId());
		if(so.getTimestamp() == null) so.setTimestamp(timestamp());

		String kee = StringUtils.isBlank(key) ? so.getId().toString() : key;
		// store
		storeBean(kee, so, cf, true, mut);

		return so.getId();
	}

	public <T extends ScooldObject> T read(Class<T> clazz, String key, CF<String> cf) {
		if(StringUtils.isBlank(key) || cf == null || clazz == null) return null;
		List<HColumn<String, String>> cols = readRow(key, cf,
				String.class, null, null, null, CasDAOFactory.DEFAULT_LIMIT, false);

		T so = fromColumns(clazz, cols);
		
		return so.getId() != null ? so : null;
	}

	public void update(ScooldObject so, CF<String> cf){
		update(so, cf, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void update(ScooldObject so, CF<String> cf, Mutator<String> mut){
		update(null, so, cf, mut);
	}

	public <SN> void update(String key, ScooldObject so, CF<String> cf, Mutator<String> mut){
		if(so == null || cf == null) return;
		String kee = (key == null) ? so.getId().toString() : key;
		String cacheKey = so.getClass().getSimpleName()
				.concat(CasDAOFactory.SEPARATOR).concat(kee);
		storeBean(kee, so, cf, false, mut);		
	}

	public void delete(ScooldObject so, CF<String > cf){
		delete(so, cf, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void delete(ScooldObject so, CF<String > cf, Mutator<String> mut){
		delete(null, so, cf, mut);
	}
	
	public <SN> void delete(String key, ScooldObject so, CF<String> cf, Mutator<String> mut){
		if(so == null || cf == null) return ;
		String kee = (key == null) ? so.getId().toString() : key;
		String cacheKey = so.getClass().getSimpleName()
				.concat(CasDAOFactory.SEPARATOR).concat(kee);
		unstoreBean(kee, so, cf, mut);
	}

	/********************************************
	 *				COLUMN FUNCTIONS
	********************************************/

	public <N, V> void putColumn(String key, CF<N> cf, N colName, V colValue){
		putColumn(key, cf, colName, colValue, 0);
	}

	public <N, V> void putColumn(String key, CF<N> cf, N colName, V colValue, int ttl){
		if(StringUtils.isBlank(key) || cf == null || colValue == null) return;

		HColumn<N, String> col = HFactory.createColumn(colName, colValue.toString(),
				getSerializer((Class<N>) colName.getClass()),
				getSerializer(String.class));

		if(ttl > 0) col.setTtl(ttl);

		mutator.insert(key, cf.getName(), col);
		mutator.discardPendingMutations();
	}

	public <SN, SUBN> void putSuperColumn(String key, SCF<SN, SUBN> cf,
			SN colName, List<HColumn<SUBN, String>> colValue){
		if(StringUtils.isBlank(key) || cf == null || colValue == null) return;
		if(colValue == null || colValue.isEmpty()) return;

		mutator.insert(key, cf.getName(), HFactory.createSuperColumn(colName,
				colValue, getSerializer((Class<SN>) colName.getClass()),
				colValue.get(0).getNameSerializer(),
				getSerializer(String.class)));
		mutator.discardPendingMutations();
	}

	public <CV, N, SUBN> CV getColumn(String key, CF<N> cf, N colName) {
		if(StringUtils.isBlank(key) || cf == null || colName == null) return null;
		HColumn<N, String> col = getHColumn(key, cf, colName);
		return (col != null) ? (CV) col.getValue() : null;
	}
	
	public Long getCounterColumn(String key){
		if(StringUtils.isBlank(key)) return null;
		HCounterColumn<String> col = getHCounterColumn(key);
		return (col != null) ? col.getValue() : 0L;
	}

	public <SN, SUBN> List<HColumn<SUBN, String>> getSuperColumn(String key,
			SCF<SN, SUBN> cf, SN colName, Class<SUBN> subColClass) {
		if(StringUtils.isBlank(key) || cf == null || colName == null) return null;
		// get the whole supercolumn
		HSuperColumn<SN, SUBN, String> scol = getHSuperColumn(key, cf, colName, subColClass);
		// note: here we return raw bytes as subcolumns!
		return (scol != null) ? scol.getColumns() : null;
	}

	public <CV, SN, SUBN> CV getSubColumn(String key, SCF<SN, SUBN> cf, SN colName,
			SUBN subColName) {
		if(StringUtils.isBlank(key) || cf == null) return null;
		HColumn<SUBN, String> col = getHSubColumn(key, cf, colName, subColName);
		return (col != null) ? (CV) col.getValue() : null;
	}
	
	public <N> void removeColumn(String key, CF<N> cf, N colName) {
		if(StringUtils.isBlank(key) || cf == null) return;
		mutator.delete(key, cf.getName(), colName,
				getSerializer((Class<N>) colName.getClass()));
		mutator.discardPendingMutations();
	}

	public <SN> void removeSuperColumn(String key, SCF<SN, ?> cf, SN colName) {
		if(StringUtils.isBlank(key) || cf == null) return;
		mutator.delete(key, cf.getName(), colName,
				getSerializer((Class<SN>) colName.getClass()));
		mutator.discardPendingMutations();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void batchPut(Column ... cols){
		batchPut(Arrays.asList(cols));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void batchPut(List<Column> cols){
		if(cols == null || cols.isEmpty()) return;
		Mutator<String> mut = createMutator();
		for (Column column : cols) {
			addInsertion(column, mut);
		}
		mut.execute();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void batchRemove(Column ... cols){
		batchRemove(Arrays.asList(cols));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void batchRemove(List<Column> cols){
		if(cols == null || cols.isEmpty()) return;
		Mutator<String> mut = createMutator();
		for (Column column : cols) {
			addDeletion(column, mut);
		}
		mut.execute();
	}

	public static <N, V> void addInsertion(Column<N, V> col, Mutator<String> mut){
		if(mut != null && col != null){
			HColumn<N, String> hCol = HFactory.createColumn(
						col.getName(), col.getValue().toString(),
					getSerializer((Class<N>) col.getName().getClass()),
					getSerializer(String.class));

			if(col.getTtl() > 0) hCol.setTtl(col.getTtl());
			mut.addInsertion(col.getKey(), col.getCf().getName(), hCol);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void addInsertions(List<Column> col, Mutator<String> mut){
		for (Column column : col) {
			addInsertion(column, mut);
		}
	}

	public static <N, V> void addDeletion(Column<N, V> col, Mutator<String> mut){
		if(mut != null && col != null){
			mut.addDeletion(col.getKey(), col.getCf().getName(), col.getName(),
					getSerializer((Class<N>) col.getName().getClass()));
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void addDeletions(List<Column> col, Mutator<String> mut){
		for (Column column : col) {
			addDeletion(column, mut);
		}
	}


	/********************************************
	 *				RAW COLUMN FUNCTIONS
	********************************************/

	public <N> HColumn<N, String> getHColumn(String key, CF<N> cf, N colName){
		if(cf == null) return null;
		HColumn<N, String> col = HFactory.createColumnQuery(keyspace,
					getSerializer(String.class),
					getSerializer((Class<N>) colName.getClass()),
					getSerializer(String.class))
				.setKey(key)
				.setColumnFamily(cf.getName())
				.setName(colName)
				.execute().get();
		return col;
	}

	public <SN, SUBN> HSuperColumn<SN, SUBN, String> getHSuperColumn(String key,
			SCF<SN, SUBN> cf, SN colName, Class<SUBN> subColClass){
		if(cf == null) return null;
		HSuperColumn<SN, SUBN, String> scol = HFactory.createSuperColumnQuery(keyspace,
					getSerializer(String.class),
					getSerializer((Class<SN>) colName.getClass()),
					getSerializer(subColClass),
					getSerializer(String.class))
				.setKey(key)
				.setColumnFamily(cf.getName())
				.setSuperName(colName)
				.execute().get();
		return scol;
	}

	public <SN, SUBN> HColumn<SUBN, String> getHSubColumn(String key,
			SCF<SN, SUBN> cf, SN colName, SUBN subColName){
		if(cf == null) return null;
		HColumn<SUBN, String> scol = HFactory.createSubColumnQuery(keyspace,
					getSerializer(String.class),
					getSerializer((Class<SN>) colName.getClass()),
					getSerializer((Class<SUBN>) subColName.getClass()),
					getSerializer(String.class))
				.setKey(key)
				.setColumnFamily(cf.getName())
				.setSuperColumn(colName)
				.setColumn(subColName)
				.execute().get();
		return scol;
	}
	
	public HCounterColumn<String> getHCounterColumn(String key){
		CounterQuery<String, String> cq = HFactory.createCounterColumnQuery(keyspace, 
				getSerializer(String.class), getSerializer(String.class));
		cq.setColumnFamily(CasDAOFactory.COUNTERS.getName());
		cq.setKey(key);
		cq.setName(CasDAOFactory.CN_COUNTS_COUNT);
		return cq.execute().get();
	}

	/********************************************
	 *				ROW FUNCTIONS
	********************************************/

	public <N> String createRow(String key, CF<N> cf, List<HColumn<N, String>> row,
			Mutator<String> mut){
		if(row == null || row.isEmpty() || cf == null) return null;

		for (HColumn<N, String> col : row){
			mut.addInsertion(key, cf.getName(), col);
		}
		
		return key;
	}

	public <SN, SUBN> String createSuperRow(String key, SCF<SN, SUBN> cf,
			List<HSuperColumn<SN, SUBN, String>> row, Mutator<String> mut){
		if(row == null || cf == null || row.isEmpty()) return null;

		for (HSuperColumn<SN, SUBN, String> col : row){
			mut.addInsertion(key, cf.getName(), col);
		}

		return key;
	}

	public <N, S> List<HColumn<N, String>> readRow(String key, CF<N> cf,
			Class<N> colNameClass, S startKey, MutableLong page,
			MutableLong itemcount, int maxItems, boolean reverse){
			
		if(StringUtils.isBlank(key) || cf == null)
			return new ArrayList<HColumn<N, String>>();

		ArrayList<HColumn<N, String>> list = new ArrayList<HColumn<N, String>>();

		SliceQuery<String, N, String> sq = HFactory.createSliceQuery(keyspace,
				getSerializer(String.class), getSerializer(colNameClass),
				getSerializer(String.class));
		sq.setKey(key);
		sq.setColumnFamily(cf.getName());
		sq.setRange((N) startKey, null, reverse, maxItems);

		list.addAll((Collection<? extends HColumn<N, String>>)
				sq.execute().get().getColumns());

		if(!list.isEmpty() && page != null){
			HColumn<?,?> last = list.get(list.size() - 1);
			Object lastk = ((HColumn<N, String>) last).getName();
			page.setValue(lastk);
			// showing max + 1 just to get the start key of next page so remove last
			if(maxItems > 1 && list.size() > maxItems){
				list.remove(list.size() - 1); //remove last
			}
		}
		// count keys
		if(itemcount != null){
			int count = countColumns(key, cf, colNameClass);
			itemcount.setValue(count);
		}

		return list;
	}

	public <SN, SUBN, S> List<HSuperColumn<SN, SUBN, String>> readSuperRow(String key,
			SCF<SN, SUBN> cf, Class<SN> superColNameClass, Class<SUBN> colNameClass,
			S startKey, MutableLong page, MutableLong itemcount,
			int maxItems, boolean reverse){

		if(StringUtils.isBlank(key) || cf == null)
			return new ArrayList<HSuperColumn<SN, SUBN, String>>();

		ArrayList<HSuperColumn<SN, SUBN, String>> list =
				new ArrayList<HSuperColumn<SN, SUBN, String>>();

			SuperRows<String, SN, SUBN, String> rows =
					HFactory.createMultigetSuperSliceQuery(keyspace,
					getSerializer(String.class), getSerializer(superColNameClass),
					getSerializer(colNameClass), getSerializer(String.class))
				.setKeys(key)
				.setColumnFamily(cf.getName())
				.setRange((SN) startKey, null, reverse, maxItems + 1)
				.execute().get();

			if(rows.getCount() == 0) return list;

			list.addAll((Collection<? extends HSuperColumn<SN, SUBN, String>>)
					rows.getByKey(key).getSuperSlice().getSuperColumns());

		if(!list.isEmpty() && page != null){
			HSuperColumn<?,?,?> last = list.get(list.size() - 1);
			Object lastk = ((HSuperColumn<SN, SUBN, String>) last).getName();
			page.setValue(lastk);
			// showing max + 1 just to get the start key of next page so remove last
			if(maxItems > 1 && list.size() > maxItems){
				list.remove(list.size() - 1); //remove last
			}
		}

		// count keys
		if(itemcount != null){
			int count = countSuperColumns(key, cf, superColNameClass);
			itemcount.setValue(count);
		}

		return list;
	}

	public void deleteRow(String key, CF<?> cf, Mutator<String> mut){
		if(StringUtils.isBlank(key) || cf == null) return;
		mut.addDeletion(key, cf.getName(), null, getSerializer(String.class));
	}

	public void deleteSuperRow(String key, SCF<?, ?> cf, Mutator<String> mut){
		if(StringUtils.isBlank(key) || cf == null) return;
		mut.addDeletion(key, cf.getName(), null, getSerializer(String.class));
	}

	/********************************************
	 *				READ ALL FUNCTIONS
	********************************************/

	public <N, T extends ScooldObject> ArrayList<T> readAll(Class<T> clazz,
			String keysKey, CF<N> keysCf, CF<String> valsCf, Class<N> colNameClass,
			N startKey, MutableLong page, MutableLong itemcount, int maxItems,
			boolean reverse, boolean colNamesAreKeys, boolean countOnlyColumns){

		if(StringUtils.isBlank(keysKey) || keysCf == null) return new ArrayList<T>();

		ArrayList<HColumn<N, String>> keys = new ArrayList<HColumn<N, String>>();
		
		// get keys from linker table
		SliceQuery<String, N, String> sq = HFactory.createSliceQuery(keyspace,
				getSerializer(String.class), 
				getSerializer(colNameClass),
				getSerializer(String.class));

		sq.setKey(keysKey);
		sq.setColumnFamily(keysCf.getName());
		sq.setRange(startKey, null, reverse, maxItems + 1);

		keys.addAll(sq.execute().get().getColumns());

		if(keys == null || keys.isEmpty()) return new ArrayList<T>();

		if(page != null){
			Long lastKey = 0L;
			if (colNamesAreKeys) {
				lastKey = (Long) keys.get(keys.size() - 1).getName();
			}else{
				lastKey =  NumberUtils.toLong(keys.get(keys.size() - 1).getValue());
			}

			page.setValue(lastKey);
//			Long startK = Arrays.equals(startKey, new byte[0]) ? 0L :
//				getLongFromBytes(startKey);

			if(maxItems > 1 && keys.size() > maxItems){
				keys.remove(keys.size() - 1); // remove last
			}
		}

		setTotalCount(clazz, keysKey, keysCf, colNameClass, itemcount, countOnlyColumns);

		ArrayList<String> keyz = new ArrayList<String>();
		for (HColumn<N, String> col : keys){
			if (colNamesAreKeys) {
				keyz.add(col.getName().toString());
			} else {
				keyz.add(col.getValue());
			}
		}

		return readAll(clazz, keyz, valsCf);
	}

	public <T extends ScooldObject> ArrayList<T> readAll(Class<T> clazz,
			List<String> keys, CF<String> cf){

		if(keys == null || keys.isEmpty() || cf == null) return new ArrayList<T>();

		ArrayList<T> list = new ArrayList<T>(keys.size());

		MultigetSliceQuery<String, String, String> q =
				HFactory.createMultigetSliceQuery(keyspace,
				getSerializer(String.class), getSerializer(String.class),
				getSerializer(String.class));

		q.setColumnFamily(cf.getName());
		q.setKeys(keys);
		q.setRange(null, null, false, CasDAOFactory.DEFAULT_LIMIT);

		try{
			for (Row<String, String, String> row : q.execute().get()) {
				T so = fromColumns(clazz, row.getColumnSlice().getColumns());
				if (so.getId() != null){
					list.add(so);
				}
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}
		return list;
	}

	/****************************************************
	 *		COLUMN EXISTANCE CHECK & COUNT FUNCTIONS
	*****************************************************/

	public <N> boolean existsColumn(String key, CF<N> cf, N columnName){
		if(StringUtils.isBlank(key)) return false;
		return getColumn(key, cf, columnName) != null;
	}

	public <SN, SUBN> boolean existsSuperColumn(String key, SCF<SN, SUBN> cf, 
			SN columnName, Class<SUBN> subColClass){
		if(StringUtils.isBlank(key)) return false;
		return getSuperColumn(key, cf, columnName, subColClass) != null;
	}

	public <SN, SUBN> boolean existsSubColumn(String key, SCF<SN, SUBN> cf,
			SN columnName, SUBN subColName){
		if(StringUtils.isBlank(key)) return false;
		return getSubColumn(key, cf, columnName, subColName) != null;
	}

	public <N, T extends ScooldObject> void setTotalCount(Class<T> clazz, String key,
			CF<N> cf, Class<N> colNameClass, MutableLong itemcount, boolean countOnlyColumns){
		if(itemcount != null){
			// count keys
			if (countOnlyColumns) {
				itemcount.setValue(countColumns(key, cf, colNameClass));
			} else {
				itemcount.setValue(getBeanCount(clazz));
			}
		}
	}

	public <N> int countColumns(String key, CF<N> cf, Class<N> colNameClass){
		if(StringUtils.isBlank(key) || cf == null) return 0;

		int result = 0;
		CountQuery<String, N> cq = HFactory.createCountQuery(keyspace,
				getSerializer(String.class),
				getSerializer(colNameClass));

		cq.setKey(key);
		cq.setColumnFamily(cf.getName());
		cq.setRange(null, null, CasDAOFactory.DEFAULT_LIMIT);

		result = cq.execute().get();
		return result;
	}

	public <SN> int countSuperColumns(String key, SCF<SN, ?> cf, Class<SN> clazz){
		if(StringUtils.isBlank(key) || cf == null) return 0;

		int result = 0;
		SuperCountQuery<String, SN> cq = HFactory.createSuperCountQuery(keyspace,
				getSerializer(String.class),
				getSerializer(clazz));

		cq.setKey(key);
		cq.setColumnFamily(cf.getName());
		cq.setRange(null, null, CasDAOFactory.DEFAULT_LIMIT);

		result = cq.execute().get();
		return result;
	}

	public <SN, SUBN> int countSubColumns(String key, SCF<SN, SUBN> cf,
			SN superCol, Class<SUBN> subColClass){
		if(StringUtils.isBlank(key) || cf == null) return 0;

		SubCountQuery<String, SN, SUBN> cq = HFactory.createSubCountQuery(keyspace,
				getSerializer(String.class),
				getSerializer((Class<SN>) superCol.getClass()),
				getSerializer(subColClass));

		cq.setKey(key);
		cq.setColumnFamily(cf.getName());
		cq.setSuperColumn(superCol);
		cq.setRange(null, null, CasDAOFactory.DEFAULT_LIMIT);

		return cq.execute().get();
	}

	public <T> Long getBeanCount(Class<T> clazz){
		return getCounterColumn(clazz.getSimpleName().toLowerCase());
	}

	protected void updateBeanCount(Class<?> clazz, boolean decrement, Mutator<String> mut){
		if (decrement) {
			mutator.decrementCounter(clazz.getSimpleName().toLowerCase(), 
					CasDAOFactory.COUNTERS.getName(), CasDAOFactory.CN_COUNTS_COUNT, 1);
		} else {
			mutator.incrementCounter(clazz.getSimpleName().toLowerCase(), 
					CasDAOFactory.COUNTERS.getName(), CasDAOFactory.CN_COUNTS_COUNT, 1);
		}
	}

	/********************************************
	 *				MISC FUNCTIONS
	********************************************/

	public boolean voteUp(Long userid, Votable<Long> votable){
		return vote(userid, votable, true, voteLockAfter, CasDAOFactory.VOTE_LOCKED_FOR_SEC);
	}
	
	public boolean voteDown(Long userid, Votable<Long> votable){
		return vote(userid, votable, false, voteLockAfter, CasDAOFactory.VOTE_LOCKED_FOR_SEC);
	}

	protected boolean vote(Long userid, Votable<Long> votable, boolean isUpvote,
			long voteLockAfter, int voteLockedForSec) {
		//no voting on your own stuff!
		if(userid == null || userid.equals(votable.getUserid()) ||
				StringUtils.isBlank(votable.getUuid())) return false;

		boolean voteSuccess = false;
//		int upOrDown = updown;
		CF<String> cf = CasDAOFactory.VOTES;
		String key = votable.getUuid();
		String colName = userid.toString();
		
		//read vote for user & uuid
		HColumn<String, String> vote = getHColumn(key, cf, colName);

		// if vote exists check timestamp for recent correction,
		// otherwise insert new vote
		Integer votes = votable.getVotes();
		Integer newVotes = votes;

		if (vote != null){
		//allow correction of vote within 2 min
			long timestamp = vote.getClock();
			long now = keyspace.createClock();
			boolean wasUpvote = BooleanUtils.toBoolean(vote.getValue()); //up or down

			if((timestamp + voteLockAfter) > now &&
					BooleanUtils.xor(new boolean[]{isUpvote, wasUpvote})) {
				// clear vote and restore votes to original count
				removeColumn(key, cf, colName);
				newVotes = wasUpvote ? --votes : ++votes;
				votable.setVotes(newVotes);
				voteSuccess = true;
			}
		}else{
			// save new vote & set expiration date to 1 month
			// users can vote again after vote lock period
			putColumn(key, cf, colName, Boolean.toString(isUpvote), voteLockedForSec);
			newVotes = isUpvote ? ++votes : --votes;
			votable.setVotes(newVotes);
			voteSuccess = true;
		}

		return voteSuccess;
	}

	protected static long convertMsTimeToCasTime(Keyspace ks, long ms){
		long t1 = ks.createClock();
		long t2 = System.currentTimeMillis();
		long delta = Math.abs(t2 - t1);

		if (delta < 100) {
		  return ms;
		} else if (delta >= 1000 ) {
		  return ms * 1000;
		} else if (delta <= 1000) {
		  return ms / 1000;
		}else{
			return ms;
		}
	}

	private <SN> void storeBean(String key, ScooldObject so, 
			CF<String> cf, boolean creation, Mutator<String> mut){
		if(so == null || cf == null || StringUtils.isBlank(cf.getName())) return;

		Long id = so.getId();
		String uuid = so.getUuid();
		Map<String, Object> propsMap = AbstractDAOUtils.getAnnotatedFields(so, Stored.class);

		propsMap.put(CasDAOFactory.CN_ID, id);
		propsMap.put(CasDAOFactory.CN_UUID, uuid);

		for (Entry<String, Object> entry : propsMap.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();

			if(value != null){
				HColumn<String, String> col = HFactory.createColumn(
						field, value.toString(), getSerializer(String.class),
						getSerializer(String.class));

				mut.addInsertion(key, cf.getName(), col);
			}else if(value == null && !creation) {
				mut.addDeletion(key, cf.getName(), field, getSerializer(String.class));
			}
		}

		//update count
		if(creation){
			if(so.getClass().equals(Post.class)){
				if(((Post) so).isAnswer()){
					updateBeanCount(Post.Answer.class, false, mut);
				}else if(((Post) so).isQuestion()){
					updateBeanCount(Post.Question.class, false, mut);
				}else if(((Post) so).isFeedback()){
					updateBeanCount(Post.Feedback.class, false, mut);
				}
			}else{
				updateBeanCount(so.getClass(), false, mut);
			}
		}
	}

	private <SN> void unstoreBean(String key, ScooldObject so, CF<String> cf, Mutator<String> mut){
		if(so == null || cf == null) return;

//		Mutator<String> mutator = getMutator();
//		mutator.addDeletion(key, cf.getName(), null, getSerializer(String.class));
//		mutator.execute();
//		mutator.discardPendingMutations();

		deleteRow(key, cf, mut);

		//update count
		if(so.getClass().equals(Post.class)){
			if(((Post) so).isAnswer()){
				updateBeanCount(Post.Answer.class, true, mut);
			}else if(((Post) so).isQuestion()){
				updateBeanCount(Post.Question.class, true, mut);
			}else if(((Post) so).isFeedback()){
				updateBeanCount(Post.Feedback.class, true, mut);
			}
		}else{
			updateBeanCount(so.getClass(), true, mut);
		}
	}

	public synchronized Long getNewId() {
		return HFactory.createClockResolution(ClockResolution.MICROSECONDS_SYNC).
				createClock() - TIMER_OFFSET;
	}

	public static java.util.UUID getTimeUUID() {
		return java.util.UUID.fromString(new com.eaio.uuid.UUID().toString());
	}

	public static String getUUID() {
		return new com.eaio.uuid.UUID().toString();
	}

	// add column to a CF with fixed size. Order by: LongType DESCENDING
	public void addTimesortColumn(String key, Long id, CF<Long> cf,
			Long time, Long oldTime){
		addTimesortColumn(key, id, cf, time, oldTime, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void addTimesortColumn(String key, Long id, CF<Long> cf,
			Long time, Long oldTime, Mutator<String> mut){

		if(time == null || id == null || cf == null) return ;
		else if(oldTime != null && oldTime.equals(time)) return;
		if(StringUtils.isBlank(key)) key = CasDAOFactory.DEFAULT_KEY;
		boolean isFull = countColumns(key, cf, Long.class) >= CasDAOFactory.MAX_COLUMNS;
		// sorted by timeID i.e. LongType
		addInsertion(new Column(key, cf, time, id.toString()), mut);

		if(isFull){
			//read last (oldest) column
			HColumn<Long, String> last = getLastColumn(key, cf, Long.class, false);
			if(last != null){
				addDeletion(new Column(key, cf, last.getName(), null), mut);
			}
		}
		//finally clean up old column
		if(oldTime != null){
			removeTimesortColumn(key, cf, oldTime, mut);
		}
	}

	public void removeTimesortColumn(String key, CF<Long> cf, Long time){
		removeTimesortColumn(key, cf, time, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void removeTimesortColumn(String key, CF<Long> cf, Long time, Mutator<String> mut){
		if(cf == null) return ;
		if(StringUtils.isBlank(key)) key = CasDAOFactory.DEFAULT_KEY;
		addDeletion(new Column(key, cf, time, null), mut);
	}

	// add a column to a CF with fixed size. Order by UTF8Type DESCENDING
	// this will create a "high score" type of list
	public void addNumbersortColumn(String key, CF<String> cf, Long id,
			Number number, Number oldNumber){
		addNumbersortColumn(key, cf, id, number, oldNumber, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void addNumbersortColumn(String key, CF<String> cf, Long id,
			Number number, Number oldNumber, Mutator<String> mut){
		addNumbersortColumn(key, cf, id, number, oldNumber, CasDAOFactory.MAX_COLUMNS, mut);
	}

	protected void addNumbersortColumn(String key, CF<String> cf, Long id,
			Number number, Number oldNumber, int maxColumns, Mutator<String> mut){

		if(oldNumber != null && oldNumber.equals(number) || cf == null) return;

		if(StringUtils.isBlank(key)) key = CasDAOFactory.DEFAULT_KEY;

		boolean isFull = countColumns(key, cf, String.class) >= maxColumns;

		String compositeKey = number.toString().concat(
				CasDAOFactory.SEPARATOR).concat(id.toString());

		if(isFull){
			//read last (oldest) column
			HColumn<String, String> last = getLastColumn(key, cf, String.class, false);

			if(last != null){
				String lastKey = last.getName();
				Long lowestNumberInList = 0L;

				if(lastKey.contains(CasDAOFactory.SEPARATOR)){
					String[] sarr = lastKey.split(CasDAOFactory.SEPARATOR);
					lowestNumberInList = NumberUtils.toLong(sarr[0], 0);

					if(number.longValue() > lowestNumberInList){
						// remove last
						addDeletion(new Column(key, cf, lastKey, null), mut);
						// add new to sort table
						addInsertion(new Column(key, cf, compositeKey, id.toString()), mut);
					}
				}
			}
		}else{
			addInsertion(new Column(key, cf, compositeKey, id.toString()), mut);
		}

		//finally clean up old column
		if(oldNumber != null){
			removeNumbersortColumn(key, cf, id, oldNumber, mut);
		}
	}

	public void removeNumbersortColumn(String key, CF<String> cf, Long id,
			Number number){
		removeNumbersortColumn(key, cf, id, number, mutator);
		mutator.execute();
		mutator.discardPendingMutations();
	}

	public void removeNumbersortColumn(String key, CF<String> cf, Long id, 
			Number number, Mutator<String> mut){

		if(cf == null || id == null || number == null) return;
		if(StringUtils.isBlank(key)) key = CasDAOFactory.DEFAULT_KEY;

		String compositeKey = number.toString().concat(CasDAOFactory.SEPARATOR)
				.concat(id.toString());
		addDeletion(new Column(key, cf, compositeKey, null), mut);
	}

	public <N> HColumn<N, String> getLastColumn(String key, CF<N> cf,
			Class<N> colNameClass, boolean reverse){
		if(cf == null) return null;
		SliceQuery<String, N, String> sq = HFactory.createSliceQuery(keyspace,
					getSerializer(String.class), getSerializer(colNameClass),
					getSerializer(String.class));
			sq.setKey(key);
			sq.setColumnFamily(cf.getName());
			sq.setRange(null, null, reverse, 1);
			ColumnSlice<N, String> slice = sq.execute().get();
		List<HColumn<N, String>> list = slice.getColumns();
		
		return list.isEmpty() ? null : list.get(0);
	}

	public <SN, SUBN> HSuperColumn<SN, SUBN, String> getLastSuperColumn(String key,
			SCF<SN, SUBN> cf, Class<SN> colNameClass, Class<SUBN> subColClass, boolean reverse){

		if(cf == null) return null;
		SuperSliceQuery<String, SN, SUBN, String> sq =
				HFactory.createSuperSliceQuery(keyspace,
					getSerializer(String.class),
					getSerializer(colNameClass),
					getSerializer(subColClass),
					getSerializer(String.class));

			sq.setKey(key);
			sq.setColumnFamily(cf.getName());
			sq.setRange(null, null, reverse, 1);
			SuperSlice<SN, SUBN, String> slice = sq.execute().get();

		List<HSuperColumn<SN, SUBN, String>> list = slice.getSuperColumns();
		
		return list.isEmpty() ? null : list.get(0);
	}

	public static Long toLong(MutableLong page){
		return (page != null && page.longValue() > 1) ?	page.longValue() : null;
	}

	public static Mutator<String> createMutator(){
		return HFactory.createMutator(keyspace, CasDAOUtils.getSerializer(String.class));
	}
	
	protected static <T extends ScooldObject> T fromColumns(Class<T> clazz,
			List<HColumn<String, String>> cols) {
		if (cols == null ) 	return null;

		T transObject = null;
		try {
			transObject = clazz.newInstance();
			for (HColumn<String, String> col : cols) {
				String name = col.getName();
				String value = col.getValue();
				//set property WITH CONVERSION
				BeanUtils.setProperty(transObject, name, value);
				//set property WITHOUT CONVERSION
//				PropertyUtils.setProperty(transObject, name, value);
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
		}

		return transObject;
	}

	public static <T> Serializer<T> getSerializer(Class<T> clazz) {
		return SerializerTypeInferer.getSerializer(clazz);
	}

}
