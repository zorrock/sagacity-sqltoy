/**
 * 
 */
package org.sagacity.sqltoy;

import static java.lang.System.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.model.SqlExecuteTrace;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供sql执行超时统计和基本的sql输出功能
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlExecuteStat.java,Revision:v1.0,Date:2015年6月12日
 * @modify {Date:2020-06-15,改进sql日志输出,将条件参数带入到sql中输出，便于开发调试}
 * @modify {Date:2020-08-12,为日志输出增加统一uid,便于辨别同一组执行语句}
 */
public class SqlExecuteStat {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlExecuteStat.class);

	/**
	 * 输出sql的策略(error/debug 两种)
	 */
	private static String printSqlStrategy = "error";

	/**
	 * 是否调试阶段
	 */
	private static boolean debug = false;

	/**
	 * 打印慢sql(单位毫秒,默认超过18秒)
	 */
	private static int printSqlTimeoutMillis = 18000;

	// 用于拟合sql中的条件值表达式(前后都以非字符和数字为依据目的是最大幅度的避免参数值里面存在问号,实际执行过程中这个问题已经被规避,但调试打印参数带入无法规避)
	private final static Pattern ARG_PATTERN = Pattern.compile("\\W\\?\\W");

	// 通过ThreadLocal 来保存进程数据
	private static ThreadLocal<SqlExecuteTrace> threadLocal = new ThreadLocal<SqlExecuteTrace>();

	/**
	 * @todo 登记开始执行
	 * @param sqlId
	 * @param type
	 * @param debugPrint
	 */
	public static void start(String sqlId, String type, Boolean debugPrint) {
		threadLocal.set(new SqlExecuteTrace(sqlId, type, (debugPrint == null) ? true : debugPrint.booleanValue()));
	}

	/**
	 * @todo 向线程中登记发生了异常,便于在finally里面明确是错误并打印相关sql
	 * @param e
	 */
	public static void error(Exception e) {
		if (threadLocal.get() != null) {
			threadLocal.get().setError(true);
		}
	}

	/**
	 * @todo 在debug模式下,在console端输出sql,便于开发人员查看
	 * @param sql
	 * @param paramValues
	 */
	public static void showSql(String sql, Object[] paramValues) {
		try {
			// debug模式直接输出
			if (debug || printSqlStrategy.equals("debug")) {
				if (threadLocal.get() != null && threadLocal.get().isPrint() == false) {
					return;
				}
				printSql(sql, paramValues, false);
			} else if (threadLocal.get() != null) {
				threadLocal.get().addSqlToyResult(sql, paramValues);
			}
		} catch (Exception e) {

		}
	}

	/**
	 * @TODO 提供中间日志输出
	 * @param message
	 * @param args
	 */
	public static void debug(String message, Object... args) {
		try {
			if (debug || printSqlStrategy.equals("debug")) {
				String uid = "";
				if (threadLocal.get() != null) {
					// 不输出sql和日志
					if (threadLocal.get().isPrint() == false) {
						return;
					}
					uid = threadLocal.get().getUid();
				}
				String debugInfo = StringUtil.fillArgs(message, args);
				if (logger.isDebugEnabled()) {
					logger.debug("UID=" + uid + "," + debugInfo);
				} else {
					out.println("UID=" + uid + "," + debugInfo);
				}
			}
		} catch (Exception e) {

		}
	}

	/**
	 * @todo 实际执行打印sql和参数
	 * @param sql
	 * @param paramValues
	 * @param isErrorOrWarn
	 */
	private static void printSql(String sql, Object[] paramValues, boolean isErrorOrWarn) {
		StringBuilder paramStr = new StringBuilder();
		boolean isDebug = logger.isDebugEnabled();
		if (paramValues != null) {
			for (int i = 0; i < paramValues.length; i++) {
				if (i > 0) {
					paramStr.append(",");
				}
				paramStr.append("p[" + i + "]=" + paramValues[i]);
			}
		}
		SqlExecuteTrace sqlTrace = threadLocal.get();
		String uid = null;
		// 这里用system.out 的原因就是给开发者在开发阶段在控制台输出sql观察程序
		if (sqlTrace != null) {
			uid = sqlTrace.getUid();
			// 异常或超时
			if (isErrorOrWarn) {
				logger.error("UID={},类型:{} ,sqlId={},发生异常!", uid, sqlTrace.getType(), sqlTrace.getId());
			} // showSql
			else {
				if (isDebug) {
					logger.debug("UID={},类型:{},sqlId={}", uid, sqlTrace.getType(), sqlTrace.getId());
				} else {
					out.println(
							StringUtil.fillArgs("UID={},类型:{},sqlId={}", uid, sqlTrace.getType(), sqlTrace.getId()));
				}
			}
		}
		if (isErrorOrWarn) {
			// 为了避免初学者误以为sqltoy执行的sql是条件拼接模式容易引入sql注入问题,故在日志中提示仅为方便调试
			logger.error("UID=" + uid + ",入参后sql={}", fitSqlParams(sql, paramValues));
			if (paramValues != null) {
				logger.error("params:{}", paramStr);
			}
		} else {
			if (isDebug) {
				logger.debug("UID=" + uid + ",入参后sql={}", fitSqlParams(sql, paramValues));
				if (paramValues != null) {
					logger.debug("params:{}", paramStr);
				}
			} else {
				out.println("UID=" + uid + ",入参后sql=" + fitSqlParams(sql, paramValues));
				if (paramValues != null) {
					out.println("params:" + paramStr);
				}
			}
		}
	}

	/**
	 * 在执行结尾时记录日志
	 */
	private static void destroyLog() {
		try {
			SqlExecuteTrace sqlTrace = threadLocal.get();
			if (sqlTrace == null) {
				return;
			}
			String uid = sqlTrace.getUid();
			long overTime = sqlTrace.getExecuteTime() - printSqlTimeoutMillis;
			// sql执行超过阀值记录日志为软件优化提供依据
			if (overTime >= 0 && sqlTrace.getStart() != null) {
				if (logger.isWarnEnabled()) {
					logger.warn("UID={},超时警告slowSql:类型:{},耗时(毫秒):{} >= {}(阀值),sqlId={}!", uid, sqlTrace.getType(),
							sqlTrace.getExecuteTime(), printSqlTimeoutMillis, sqlTrace.getId());
				} else {
					out.println(StringUtil.fillArgs("UID={},超时警告slowSql:类型:{},耗时(毫秒):{} >= {}(阀值),sqlId={}!", uid,
							sqlTrace.getType(), sqlTrace.getExecuteTime(), printSqlTimeoutMillis, sqlTrace.getId()));
				}
			} // 未超时也未发生错误,无需打印日志
			else if ((debug || printSqlStrategy.equals("debug")) && sqlTrace.isPrint()) {
				if (logger.isDebugEnabled()) {
					logger.debug("UID={},sqlId={},耗时:{}毫秒!", uid, sqlTrace.getId(), sqlTrace.getExecuteTime());
				} else {
					out.println(StringUtil.fillArgs("UID={},sqlId={},耗时:{}毫秒!", uid, sqlTrace.getId(),
							sqlTrace.getExecuteTime()));
				}
			}
			// 输出错误日志
			if (sqlTrace.isError()) {
				List<SqlToyResult> sqlToyResults = sqlTrace.getSqlToyResults();
				for (SqlToyResult sqlResult : sqlToyResults) {
					printSql(sqlResult.getSql(), sqlResult.getParamsValue(), true);
				}
			}
		} catch (Exception e) {

		}
	}

	/**
	 * 清理线程中的数据
	 */
	public static void destroy() {
		// 执行完成时打印日志
		destroyLog();
		threadLocal.remove();
		threadLocal.set(null);
	}

	/**
	 * @param printSqlStrategy the printSqlStrategy to set
	 */
	public static void setPrintSqlStrategy(String printSqlStrategy) {
		SqlExecuteStat.printSqlStrategy = printSqlStrategy.toLowerCase();
	}

	/**
	 * @param debug the debug to set
	 */
	public static void setDebug(boolean debug) {
		SqlExecuteStat.debug = debug;
	}

	/**
	 * @param printSqlTimeoutMillis the printSqlTimeoutMillis to set
	 */
	public static void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		SqlExecuteStat.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

	/**
	 * @TODO 将参数值拟合到sql中作为debug输出,便于开发进行调试(2020-06-15)
	 * @param sql
	 * @param params
	 * @return
	 */
	private static String fitSqlParams(String sql, Object[] params) {
		if (params == null || params.length == 0) {
			return sql;
		}
		StringBuilder lastSql = new StringBuilder();
		// 补空的目的在于迎合匹配规则
		Matcher matcher = ARG_PATTERN.matcher(sql.concat(" "));
		int start = 0;
		int end;
		int index = 0;
		int paramSize = params.length;
		Object paramValue;
		// 逐个查找?用实际参数值进行替换
		while (matcher.find(start)) {
			end = matcher.start() + 1;
			lastSql.append(sql.substring(start, end));
			if (index < paramSize) {
				paramValue = params[index];
				// 字符
				if (paramValue instanceof CharSequence) {
					lastSql.append("'" + paramValue + "'");
				} else if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + "'");
				} else if (paramValue instanceof LocalDate) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + "'");
				} else if (paramValue instanceof LocalTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "HH:mm:ss") + "'");
				} else if (paramValue instanceof Object[]) {
					lastSql.append(combineArray((Object[]) paramValue));
				} else if (paramValue instanceof Collection) {
					lastSql.append(combineArray(((Collection) paramValue).toArray()));
				} else {
					lastSql.append("" + paramValue);
				}
			} else {
				// 问号数量大于参数值数量,说明sql中存在写死的条件值里面存在问号,因此不再进行条件值拟合
				return sql;
			}
			// 正则匹配最后是\\W,所以要-1
			start = matcher.end() - 1;
			index++;
		}
		lastSql.append(sql.substring(start));
		return lastSql.toString();
	}

	/**
	 * @TODO 组合in参数
	 * @param array
	 * @return
	 */
	private static String combineArray(Object[] array) {
		if (array == null || array.length == 0) {
			return " null ";
		}
		StringBuilder result = new StringBuilder();
		Object value;
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			value = array[i];
			if (value instanceof CharSequence) {
				result.append("'" + value + "'");
			} else if (value instanceof Date || value instanceof LocalDateTime) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss") + "'");
			} else if (value instanceof LocalDate) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd") + "'");
			} else if (value instanceof LocalTime) {
				result.append("'" + DateUtil.formatDate(value, "HH:mm:ss") + "'");
			} else {
				result.append("" + value);
			}
		}
		return result.toString();
	}
}
