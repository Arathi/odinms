package net.sf.odinms.database;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Properties;

public class DatabaseConnection {

    private static final HashMap<Integer, ConWrapper> connections = new HashMap<Integer, ConWrapper>();
    private static String dbDriver;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static boolean propsInited = false;
    private static Properties dbProps = new Properties();
    private static long connectionTimeOut = 300000;
    public static final int CLOSE_CURRENT_RESULT = 1;
    public static final int KEEP_CURRENT_RESULT = 2;
    public static final int CLOSE_ALL_RESULTS = 3;
    public static final int SUCCESS_NO_INFO = -2;
    public static final int EXECUTE_FAILED = -3;
    public static final int RETURN_GENERATED_KEYS = 1;
    public static final int NO_GENERATED_KEYS = 2;

    public static Connection getConnection() {
        Thread cThread = Thread.currentThread();
        int threadID = (int) cThread.getId();
        ConWrapper ret = connections.get(threadID);
        if (ret == null) {
            Connection retCon = connectToDB();
            ret = new ConWrapper(retCon);
            ret.id = threadID;
            connections.put(threadID, ret);
        }

        return ret.getConnection();
    }

    private static long getWaitTimeout(Connection con) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.createStatement();
            rs = stmt.executeQuery("SHOW VARIABLES LIKE 'wait_timeout'");
            if (rs.next()) {
                return Math.max(1000, rs.getInt(2) * 1000 - 1000);
            } else {
                return -1;
            }
        } catch (SQLException ex) {
            return -1;
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex1) {
                } finally {
                    if (rs != null) {
                        try {
                            rs.close();
                        } catch (SQLException ex1) {
                        }
                    }
                }
            }
        }
    }

    private static Connection connectToDB() {
        if (!propsInited) {
            try {
                FileReader fR = new FileReader("服务端配置.ini");
                dbProps.load(fR);
                fR.close();
            } catch (IOException ex) {
                System.err.println("加载数据库配置出错，请检查" + ex);
                //throw new DatabaseException(ex);
            }
            dbDriver = dbProps.getProperty("driver");
            dbUrl = dbProps.getProperty("url");
            dbUser = dbProps.getProperty("user");
            dbPass = dbProps.getProperty("password");
            try {
                connectionTimeOut = Long.parseLong(dbProps.getProperty("timeout"));
            } catch (Exception e) {
                System.out.println("[DB信息] Cannot read Timeout Information, using default: " + connectionTimeOut + " ");
            }
        }
        try {
            Class.forName(dbDriver);
        } catch (ClassNotFoundException e) {
            System.out.println("[DB信息] Could not locate the JDBC mysql driver.");
        }
        try {
            Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass);
            if (!propsInited) {
                long timeout = getWaitTimeout(con);
                if (timeout == -1) {
                    System.out.println("[DB信息] Failed to read Wait_Timeout, using " + connectionTimeOut + " instead.");
                } else {
                    connectionTimeOut = timeout;
                    System.out.println("数据库正在加载.请稍等....");
                }
                propsInited = true;
            }
            return con;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }

    }

    public static void closeAll() throws SQLException {
        for (ConWrapper con : connections.values()) {
            con.connection.close();
        }
        connections.clear();
    }

    public static class ConWrapper {

        private long lastAccessTime = 0;
        private Connection connection;
        private int id;

        public ConWrapper(Connection con) {
            connection = con;
        }

        public Connection getConnection() {
            if (expiredConnection()) {
                System.out.println("[DB信息] 连接 " + id + " 已经超时.重新连接...");
                try {
                    connection.close();
                } catch (Throwable err) {
                }
                connection = connectToDB();
            }
            lastAccessTime = System.currentTimeMillis();
            return connection;
        }

        public boolean expiredConnection() {
            if (lastAccessTime == 0) {
                return false;
            }
            try {
                return (System.currentTimeMillis() - lastAccessTime >= DatabaseConnection.connectionTimeOut) || (connection.isClosed());
            } catch (Throwable ex) {
                return true;
            }
        }
    }
}
