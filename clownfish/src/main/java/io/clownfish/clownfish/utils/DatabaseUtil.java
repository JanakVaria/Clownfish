/*
 * Copyright 2019 sulzbachr.
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
 */
package io.clownfish.clownfish.utils;

import io.clownfish.clownfish.dbentities.CfDatasource;
import io.clownfish.clownfish.dbentities.CfSitedatasource;
import io.clownfish.clownfish.jdbc.DatatableCondition;
import io.clownfish.clownfish.jdbc.DatatableDeleteProperties;
import io.clownfish.clownfish.jdbc.DatatableNewProperties;
import io.clownfish.clownfish.jdbc.DatatableProperties;
import io.clownfish.clownfish.jdbc.DatatableUpdateProperties;
import io.clownfish.clownfish.jdbc.JDBCUtil;
import io.clownfish.clownfish.jdbc.TableField;
import io.clownfish.clownfish.jdbc.TableFieldStructure;
import io.clownfish.clownfish.serviceinterface.CfDatasourceService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author sulzbachr
 */
@Component
public class DatabaseUtil {
    @Autowired CfDatasourceService cfdatasourceService;
    
    final transient Logger LOGGER = LoggerFactory.getLogger(DatabaseUtil.class);

    public DatabaseUtil() {
    }
    
    public HashMap<String, HashMap> getDbexport(List<CfSitedatasource> sitedatasourcelist, HashMap<String, DatatableProperties> datatableproperties, HashMap<String, DatatableNewProperties> datatablenewproperties, HashMap<String, DatatableDeleteProperties> datatabledeleteproperties, HashMap<String, DatatableUpdateProperties> datatableupdateproperties) {
        HashMap<String, HashMap> dbexport = new HashMap<>();
        for (CfSitedatasource sitedatasource : sitedatasourcelist) {
            CfDatasource cfdatasource = cfdatasourceService.findById(sitedatasource.getCfSitedatasourcePK().getDatasourceref());

            JDBCUtil jdbcutil = new JDBCUtil(cfdatasource.getDriverclass(), cfdatasource.getUrl(), cfdatasource.getUser(), cfdatasource.getPassword());
            Connection con = jdbcutil.getConnection();
            if (null != con) {
                try {
                    DatabaseMetaData dmd = con.getMetaData();

                    ResultSet resultSetTables = dmd.getTables(null, null, null, new String[]{"TABLE"});

                    HashMap<String, ArrayList> dbtables = new HashMap<>();
                    HashMap<String, Object> dbvalues = new HashMap<>();
                    while(resultSetTables.next())
                    {
                        String tablename = resultSetTables.getString("TABLE_NAME");
                        //System.out.println(tablename);
                        if (datatableproperties.get(tablename) != null) {
                            manageTableRead(con, dmd, tablename, datatableproperties, dbtables, dbvalues);
                        }
                        if (datatablenewproperties.get(tablename) != null) {
                            boolean ok = manageTableInsert(con, dmd, tablename, datatablenewproperties, dbtables, dbvalues);
                            if (ok) {
                                dbvalues.put("INSERT", "true");
                            } else {
                                dbvalues.put("INSERT", "false");
                            }
                        }
                        if (datatabledeleteproperties.get(tablename) != null) {
                            boolean ok = manageTableDelete(con, dmd, tablename, datatabledeleteproperties, dbtables, dbvalues);
                            if (ok) {
                                dbvalues.put("DELETE", "true");
                            } else {
                                dbvalues.put("DELETE", "false");
                            }
                        }
                        if (datatableupdateproperties.get(tablename) != null) {
                            boolean ok = manageTableUpdate(con, dmd, tablename, datatableupdateproperties, dbtables, dbvalues);
                            if (ok) {
                                dbvalues.put("UPDATE", "true");
                            } else {
                                dbvalues.put("UPDATE", "false");
                            }
                        }
                    }

                    resultSetTables = dmd.getTables(null, null, null, new String[]{"VIEW"});
                    while(resultSetTables.next())
                    {
                        String tablename = resultSetTables.getString("TABLE_NAME");
                        //System.out.println(tablename);
                        if (datatableproperties.get(tablename) != null) {
                            manageTableRead(con, dmd, tablename, datatableproperties, dbtables, dbvalues);
                        }
                    }

                    dbvalues.put("table", dbtables);
                    dbexport.put(cfdatasource.getDatabasename(), dbvalues);
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            } else {
                return null;
            }
        }
        return dbexport;
    }
    
    private void manageTableRead(Connection con, DatabaseMetaData dmd, String tablename, HashMap<String, DatatableProperties> datatableproperties, HashMap<String, ArrayList> dbtables, HashMap<String, Object> dbvalues) {
        Statement stmt = null;
        ResultSet result = null;
        try {
            long low_limit = 1;
            long high_limit = 50;
            
            //System.out.println(con.getMetaData().getDriverName());

            String default_order = "";
            TableFieldStructure tfs = getTableFieldsList(dmd, tablename, default_order);
            default_order = tfs.getDefault_order();
            
            String default_direction = "ASC";
            DatatableProperties dtp = datatableproperties.get(tablename);
            if (dtp != null) {
                if (dtp.getOrderby() != null) {
                    default_order = datatableproperties.get(tablename).getOrderby();
                }
                if (dtp.getOrderdir() != null) {
                    default_direction = datatableproperties.get(tablename).getOrderdir();
                }
                low_limit = 1L + (long)((dtp.getPage()-1) * dtp.getPagination());
                if (con.getMetaData().getDriverName().contains("MS SQL")) {
                    high_limit = (long) dtp.getPage() * (long) dtp.getPagination();
                }
                if (con.getMetaData().getDriverName().contains("MySQL")) {
                    high_limit = dtp.getPagination();
                }
            }
            
            StringBuilder sql_count = new StringBuilder();
            StringBuilder sql_outer = new StringBuilder();
            StringBuilder sql_inner = new StringBuilder();
            
            if (con.getMetaData().getDriverName().contains("MS SQL")) {
                sql_outer.append("SELECT ");
                sql_inner.append("SELECT ");
                sql_count.append("SELECT COUNT(*) AS count FROM ");
                
                if ((dtp != null) && (dtp.getGroupbylist().size() > 0)) {
                    sql_outer.append("count(*) AS groupbycount, ");
                    tfs.getTableFieldsList().stream().filter((tf) -> (dtp.getGroupbylist().contains(tf.getName()))).map((tf) -> {
                        sql_outer.append(tf.getName());
                        return tf;
                    }).map((tf) -> {
                        sql_outer.append(", ");
                        sql_inner.append(tf.getName());
                        return tf;
                    }).forEach((_item) -> {
                        sql_inner.append(", ");
                    });
                } else {
                    tfs.getTableFieldsList().stream().map((tf) -> {
                        sql_outer.append(tf.getName());
                        return tf;
                    }).map((tf) -> {
                        sql_outer.append(", ");
                        sql_inner.append(tf.getName());
                        return tf;
                    }).forEach((_item) -> {
                        sql_inner.append(", ");
                    });
                }
                sql_count.append(tablename);
                sql_outer.delete(sql_outer.length()-2, sql_outer.length());
                sql_outer.append(" FROM (");
                sql_inner.append("ROW_NUMBER() OVER (ORDER BY ");
                sql_inner.append(default_order);
                sql_inner.append(" ");
                sql_inner.append(default_direction);
                sql_inner.append(" ) AS rownumber FROM ");
                sql_inner.append(tablename);
                StringBuilder sql_condition = null;
                if (dtp != null) {
                    sql_condition = buildCondition(dtp.getConditionlist(), tfs.getTableFieldsList());
                }
                sql_inner.append(sql_condition);
                sql_count.append(sql_condition);
                StringBuilder sql_groupby = null;
                if (dtp != null) {
                    sql_groupby = buildGroupBy(dtp.getGroupbylist());
                }
                sql_inner.append(sql_groupby);
                if (dtp != null) {
                    if (!dtp.getGroupbycount().isEmpty()) {
                        sql_inner.append(" ,");
                        sql_inner.append(dtp.getGroupbycount());
                    }
                }
                sql_count.append(sql_groupby);
                
                sql_outer.append(sql_inner);
                sql_outer.append(") orderedselection WHERE rownumber between ");
                sql_outer.append(low_limit);
                sql_outer.append(" AND ");
                sql_outer.append(high_limit);
                sql_outer.append(sql_groupby);
            } 
            if (con.getMetaData().getDriverName().contains("MySQL")) {
                sql_outer.append("SELECT ");
                sql_count.append("SELECT COUNT(*) AS count FROM ");
                
                if ((dtp != null) && (dtp.getGroupbylist().size() > 0)) {
                    sql_outer.append("count(*) AS groupbycount, ");
                    tfs.getTableFieldsList().stream().filter((tf) -> (dtp.getGroupbylist().contains(tf.getName()))).map((tf) -> {
                        sql_outer.append(tf.getName());
                        return tf;
                    }).forEach((_item) -> {
                        sql_outer.append(", ");
                    });
                } else {
                    tfs.getTableFieldsList().stream().map((tf) -> {
                        sql_outer.append(tf.getName());
                        return tf;
                    }).forEach((_item) -> {
                        sql_outer.append(", ");
                    });
                }
                
                sql_count.append(tablename);
                sql_outer.delete(sql_outer.length()-2, sql_outer.length());
                sql_outer.append(" FROM ");
                sql_outer.append(tablename);
                StringBuilder sql_condition = null;
                if (dtp != null) {
                    sql_condition = buildCondition(dtp.getConditionlist(), tfs.getTableFieldsList());
                }
                sql_outer.append(sql_condition);
                sql_count.append(sql_condition);
                
                StringBuilder sql_groupby = null;
                if (dtp != null) {
                    sql_groupby = buildGroupBy(dtp.getGroupbylist());
                }
                sql_outer.append(sql_groupby);
                if (dtp != null) {
                    if (!dtp.getGroupbycount().isEmpty()) {
                        sql_outer.append(" ,");
                        sql_outer.append(dtp.getGroupbycount());
                    }
                }
                sql_count.append(sql_groupby);
                
                sql_outer.append(" LIMIT ");
                sql_outer.append(low_limit-1);
                sql_outer.append(", ");
                sql_outer.append(high_limit);
                sql_outer.append(sql_groupby);
            }

            if (dtp != null) {
                if (!dtp.getGroupbycount().isEmpty()) {
                    TableField groupbycountfield = new TableField();
                    groupbycountfield.setName("groupbycount");
                    groupbycountfield.setType("INT");
                    tfs.getTableFieldsList().add(groupbycountfield);
                }
            }
            
            stmt = con.createStatement();
            result = stmt.executeQuery(sql_outer.toString());
            ArrayList<HashMap> tablevalues = new ArrayList<>();
            while (result.next()) {
                HashMap<String, String> dbexportvalues = new HashMap<>();
                for (TableField tf : tfs.getTableFieldsList()) {
                    try {
                        String value = result.getString(tf.getName());
                        dbexportvalues.put(tf.getName(), value);
                    } catch (java.sql.SQLException ex) {
                        
                    }
                }
                tablevalues.add(dbexportvalues);
            }
            dbtables.put(tablename, tablevalues);
            try {
                result.close();
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage());
            }
            result = stmt.executeQuery(sql_count.toString());
            HashMap<String, String> dbexportvalues = new HashMap<>();
            while (result.next()) {
                String value = result.getString("count");
                dbexportvalues.put("count", value);
            }
            dbvalues.put(tablename, dbexportvalues);
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage());
        } finally {
            if (null != stmt) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            }
            if (null != result) {
                try {
                    result.close();
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            }
        }
    }
    
    private boolean manageTableInsert(Connection con, DatabaseMetaData dmd, String tablename, HashMap<String, DatatableNewProperties> datatablenewproperties, HashMap<String, ArrayList> dbtables, HashMap<String, Object> dbvalues) {
        Statement stmt = null;
        try {
            TableFieldStructure tfs = getTableFieldsList(dmd, tablename, "");
            DatatableNewProperties dtnp = datatablenewproperties.get(tablename);
            
            StringBuilder sql_insert_fields = new StringBuilder();
            StringBuilder sql_insert_values = new StringBuilder();
            dtnp.getValuelist().stream().map((dtnv) -> {
                sql_insert_fields.append(dtnv.getField());
                return dtnv;
            }).forEach((dtnv) -> {
                sql_insert_fields.append(", ");
                String fieldType = getFieldType(tfs.getTableFieldsList(), dtnv.getField());
                if (null != fieldType) {
                    if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                        sql_insert_values.append("'");
                    }
                    sql_insert_values.append(dtnv.getValue());
                    if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                        sql_insert_values.append("'");
                    }
                    sql_insert_values.append(", ");
                }
            });
            sql_insert_fields.delete(sql_insert_fields.length()-2, sql_insert_fields.length());
            sql_insert_values.delete(sql_insert_values.length()-2, sql_insert_values.length());
            
            StringBuilder sql_insert = new StringBuilder();
            sql_insert.append("INSERT INTO ");
            sql_insert.append(tablename);
            sql_insert.append(" (");
            sql_insert.append(sql_insert_fields);
            sql_insert.append(") VALUES (");
            sql_insert.append(sql_insert_values);
            sql_insert.append(")");
            
            stmt = con.createStatement();
            int count = stmt.executeUpdate(sql_insert.toString());
            boolean ok = false;
            if (count > 0 ) {
                ok = true;
            }
            return ok;
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage());
            return false;
        } finally {
            if (null != stmt) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            }
        }
    }
    
    private boolean manageTableDelete(Connection con, DatabaseMetaData dmd, String tablename, HashMap<String, DatatableDeleteProperties> datatabledeleteproperties, HashMap<String, ArrayList> dbtables, HashMap<String, Object> dbvalues) {
        Statement stmt = null;
        try {
            TableFieldStructure tfs = getTableFieldsList(dmd, tablename, "");
            DatatableDeleteProperties dtdp = datatabledeleteproperties.get(tablename);
            
            StringBuilder sql_condition = new StringBuilder();
            if (dtdp != null) {
                sql_condition.append(" WHERE ");
                dtdp.getValuelist().stream().map((dtdv) -> {
                    sql_condition.append("(");
                    sql_condition.append(dtdv.getField());
                    return dtdv;
                }).forEach((dtdv) -> {
                    String fieldType = getFieldType(tfs.getTableFieldsList(), dtdv.getField());
                    sql_condition.append(" = ");
                    if (null != fieldType) {
                        if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                            sql_condition.append("'");
                        }
                        sql_condition.append(dtdv.getValue());
                        if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                            sql_condition.append("'");
                        }
                        sql_condition.append(") AND ");
                    }
                });
                sql_condition.delete(sql_condition.length()-4, sql_condition.length());
            }
            
            StringBuilder sql_delete = new StringBuilder();
            sql_delete.append("DELETE FROM ");
            sql_delete.append(tablename);
            sql_delete.append(sql_condition);
            
            stmt = con.createStatement();
            int count = stmt.executeUpdate(sql_delete.toString());
            boolean ok = false;
            if (count > 0 ) {
                ok = true;
            }
            
            return ok;
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage());
            return false;
        } finally {
            if (null != stmt) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            }
        }
    }
    
    private boolean manageTableUpdate(Connection con, DatabaseMetaData dmd, String tablename, HashMap<String, DatatableUpdateProperties> datatableproperties, HashMap<String, ArrayList> dbtables, HashMap<String, Object> dbvalues) {
        Statement stmt = null;
        try {
            TableFieldStructure tfs = getTableFieldsList(dmd, tablename, "");
            DatatableUpdateProperties dtup = datatableproperties.get(tablename);
            
            StringBuilder sql_update_values = new StringBuilder();
            dtup.getValuelist().stream().map((dtuv) -> {
                sql_update_values.append(dtuv.getField());
                return dtuv;
            }).forEach((dtuv) -> {
                sql_update_values.append(" = ");
                String fieldType = getFieldType(tfs.getTableFieldsList(), dtuv.getField());
                if (null != fieldType) {
                    if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                        sql_update_values.append("'");
                    }
                    sql_update_values.append(dtuv.getValue());
                    if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                        sql_update_values.append("'");
                    }
                    sql_update_values.append(", ");
                }
            });
            sql_update_values.delete(sql_update_values.length()-2, sql_update_values.length());
            
            StringBuilder sql_update = new StringBuilder();
            sql_update.append("UPDATE ");
            sql_update.append(tablename);
            sql_update.append(" SET ");
            sql_update.append(sql_update_values);
            StringBuilder sql_condition = new StringBuilder();
            if (null != dtup) {
                sql_condition = buildCondition(dtup.getConditionlist(), tfs.getTableFieldsList());
            }
            sql_update.append(sql_condition);
            
            stmt = con.createStatement();
            int count = stmt.executeUpdate(sql_update.toString());
            boolean ok = false;
            if (count > 0 ) {
                ok = true;
            }
            return ok;
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage());
            return false;
        } finally {
            if (null != stmt) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    LOGGER.error(ex.getMessage());
                }
            }
        }
    }
    
    private TableFieldStructure getTableFieldsList(DatabaseMetaData dmd, String tablename, String default_order) {
        try {
            TableFieldStructure tfs = new TableFieldStructure();
            List<String> pkList = new ArrayList<>();
            ResultSet resultSetPK = dmd.getPrimaryKeys(null, null, tablename);
            int counter = 0;
            while(resultSetPK.next())
            {
                pkList.add(resultSetPK.getString("COLUMN_NAME"));
                if (counter == 0) {
                    default_order = resultSetPK.getString("COLUMN_NAME");
                }
                counter++;
            }
            
            ArrayList<TableField> tableFieldsList = new ArrayList<>();
            ResultSet columns = dmd.getColumns(null, null, tablename, null);
            while(columns.next())
            {
                String columnName = columns.getString("COLUMN_NAME");
                String datatype = columns.getString("DATA_TYPE");
                String colomuntypename = columns.getString("TYPE_NAME");
                String columnsize = columns.getString("COLUMN_SIZE");
                String decimaldigits = columns.getString("DECIMAL_DIGITS");
                if (decimaldigits == null) {
                    decimaldigits = "0";
                }
                String isNullable = columns.getString("IS_NULLABLE");
                //String is_autoIncrment = columns.getString("IS_AUTOINCREMENT");
                String is_autoIncrment = "";
                
                if ((default_order.isEmpty()) && (counter == 0)) {
                    default_order = columnName;
                }
                TableField tf;
                switch (datatype) {
                    case "1":      // varchar -> String
                    case "12":
                    case "2005":    
                        tf = new TableField(columnName, "STRING", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;
                    case "2":       // int
                    case "4":
                    case "5":    
                        tf = new TableField(columnName, "INT", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;
                    case "7":       // real
                        tf = new TableField(columnName, "REAL", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;            
                    case "8":       // float
                        tf = new TableField(columnName, "FLOAT", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;    
                    case "-5":      // long
                        tf = new TableField(columnName, "LONG", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;
                    case "-7":      // bit
                        tf = new TableField(columnName, "BOOLEAN", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;    
                    case "93":      // Date
                        tf = new TableField(columnName, "DATE", colomuntypename, pkList.contains(columnName), Integer.parseInt(columnsize), Integer.parseInt(decimaldigits), isNullable);
                        tableFieldsList.add(tf);
                        break;
                }
            }
            tfs.setDefault_order(default_order);
            tfs.setTableFieldsList(tableFieldsList);
            return tfs;
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage());
            return null;
        }
    }
    
    private StringBuilder buildGroupBy(ArrayList<String> groupbylist) {
        StringBuilder sql_groupby = new StringBuilder();
        if (!groupbylist.isEmpty()) {
            sql_groupby.append(" GROUP BY ");
            groupbylist.stream().map((groupby) -> {
                sql_groupby.append(groupby);
                return groupby;
            }).forEach((_item) -> {
                sql_groupby.append(", ");
            });
            sql_groupby.delete(sql_groupby.length()-2, sql_groupby.length());
        }
        return sql_groupby;
    }
    
    private StringBuilder buildCondition(ArrayList<DatatableCondition> conditionlist, ArrayList<TableField> tableFieldsList) {
        StringBuilder sql_condition = new StringBuilder();
        if (!conditionlist.isEmpty()) {
            sql_condition.append(" WHERE ");
            for (DatatableCondition dtc : conditionlist) {
                sql_condition.append("(");
                sql_condition.append(dtc.getField());
                String fieldType = getFieldType(tableFieldsList, dtc.getField());
                if (null != fieldType) {
                    switch (dtc.getOperand()) {
                        case "eq" : 
                            sql_condition.append(" = ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;
                        case "lk" : 
                            sql_condition.append(" LIKE ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0) || (fieldType.compareToIgnoreCase("int") == 0) || (fieldType.compareToIgnoreCase("float") == 0)) {
                                sql_condition.append("'%");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0) || (fieldType.compareToIgnoreCase("int") == 0) || (fieldType.compareToIgnoreCase("float") == 0)) {
                                sql_condition.append("%'");
                            }
                            break;
                        case "gt" : 
                            sql_condition.append(" > ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;
                        case "ge" : 
                            sql_condition.append(" >= ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;    
                        case "lt" : 
                            sql_condition.append(" < ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;
                        case "le" : 
                            sql_condition.append(" <= ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;    
                        case "neq" : 
                            sql_condition.append(" <> ");
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            sql_condition.append(dtc.getValue());
                            if ((fieldType.compareToIgnoreCase("string") == 0) || (fieldType.compareToIgnoreCase("date") == 0)) {
                                sql_condition.append("'");
                            }
                            break;
                    }
                    sql_condition.append(") AND ");
                }
            }
            sql_condition.delete(sql_condition.length()-4, sql_condition.length());
        }
        return sql_condition;
    }
    
    private String getFieldType(ArrayList<TableField> tableFieldsList, String fieldname) {
        for (TableField tf : tableFieldsList) {
            if (tf.getName().compareToIgnoreCase(fieldname) == 0) {
                return tf.getType();
            }
        }
        return null;
    }
}
