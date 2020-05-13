package com.xdsty.datasync.db.init;

import com.xdsty.datasync.constant.MySQLCommonSql;
import com.xdsty.datasync.enums.IndexTypeEnum;
import com.xdsty.datasync.pojo.Column;
import com.xdsty.datasync.pojo.ColumnSchema;
import com.xdsty.datasync.pojo.DBInfo;
import com.xdsty.datasync.pojo.Index;
import com.xdsty.datasync.pojo.MTable;
import com.xdsty.datasync.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 张富华
 * @date 2020/3/17 16:06
 */
@Component
public class MySqlInfoInit implements DBInit {

    private static final Logger log = LoggerFactory.getLogger(MySqlInfoInit.class);

    @Override
    public void initDbInfo(DBInfo dbInfo) throws SQLException, ClassNotFoundException {
        dbInfo.initConnection();
        // 获取所有表
        initTableInfo(dbInfo);
        initTableColumn(dbInfo);
        initTableIndex(dbInfo);
    }

    /**
     * 初始化表信息
     * @param dbInfo 数据库信息
     */
    private void initTableInfo(DBInfo dbInfo) throws SQLException {
        Connection conn = dbInfo.getConnection();
        List<String> tableNames = getTableNames(conn);
        List<MTable> tables = new LinkedList<>();
        for(String tableName : tableNames){
            MTable table = new MTable();
            ResultSet set = conn.prepareStatement(MySQLCommonSql.getShowCreateTable(tableName)).executeQuery();
            if (set.next()) {
                table.initTable(set.getString(2));
            }
            table.setTableName(tableName);
            tables.add(table);
        }
        dbInfo.setTables(tables);
    }

    /**
     * 获取所有表 并按照表名排序
     *
     * @return 表名列表
     */
    private List<String> getTableNames(Connection conn) throws SQLException {
        log.error("开始获取源数据库表，{}", DateUtil.date2String(new Date(), DateUtil.DATE_TIME_PATTERN));
        PreparedStatement statement;
        List<String> list = new LinkedList<>();
        try {
            statement = conn.prepareStatement(MySQLCommonSql.getSelectTableSql());
            ResultSet set = statement.executeQuery();
            while (set.next()) {
                list.add(set.getString(1));
            }
        } catch (SQLException e) {
            log.error("获取数据库表失败，{}", e);
            throw e;
        }
        log.error("获取源数据库表成功，{}", DateUtil.date2String(new Date(), DateUtil.DATE_TIME_PATTERN));
        return list.stream().sorted(Comparator.comparing(String::toString)).collect(Collectors.toList());
    }

    /**
     * 初始化表的列信息
     * @param dbInfo 数据库信息
     */
    private void initTableColumn(DBInfo dbInfo) throws SQLException {
        if(CollectionUtils.isEmpty(dbInfo.getTables())){
            return;
        }
        Connection conn = dbInfo.getConnection();
        List<MTable> tables = dbInfo.getTables();
        for(MTable table : tables){
            ResultSet set = conn.prepareStatement(MySQLCommonSql.getSelectColumns(table.getTableName())).executeQuery();
            List<Column> columns = new LinkedList<>();
            while (set.next()){
                Column column = new Column();
                column.setColumnName(set.getString(Column.FIELD));
                column.setType(set.getString(Column.TYPE));
                column.setCanBeNull(set.getString(Column.NULL).equals(Column.CAN_NULL) ? Boolean.TRUE : Boolean.FALSE);
                column.setDefaultVal(set.getString(Column.DEFAULT));
                column.setTableName(table.getTableName());
                columns.add(column);
            }
            table.setColumns(columns.stream().sorted(Comparator.comparing(Column::getColumnName)).collect(Collectors.toList()));
        }
    }

    /**
     * 根据schema初始化表信息
     * @param dbInfo 数据库
     */
    private void initTableColumnWithSchema(DBInfo dbInfo) throws SQLException {
        if(CollectionUtils.isEmpty(dbInfo.getTables())){
            return;
        }
        Connection conn = dbInfo.getConnection();
        for(MTable table : dbInfo.getTables()){
            ResultSet set = conn.prepareStatement(MySQLCommonSql.getColumnSchema(table.getTableName())).executeQuery();
            List<ColumnSchema> columns = new LinkedList<>();
            while (set.next()){
                ColumnSchema column = new ColumnSchema();
                column.setTableCatalog(set.getString(ColumnSchema.TABLE_CATALOG));
                column.setTableSchema(set.getString(ColumnSchema.TABLE_SCHEMA));
                column.setTableName(set.getString(ColumnSchema.TABLE_NAME));
                column.setColumnName(set.getString(ColumnSchema.COLUMN_NAME));
                column.setOrdinalPosition(set.getInt(ColumnSchema.ORDINAL_POSITION));
                column.setColumnDefault(set.getString(ColumnSchema.COLUMN_DEFAULT));
                column.setNullable(set.getString(ColumnSchema.IS_NULLABLE));
                column.setDataType(set.getString(ColumnSchema.DATA_TYPE));
                column.setCharacterMaximumLength(set.getInt(ColumnSchema.CHARACTER_MAXIMUM_LENGTH));
                column.setCharacterOctetLength(set.getInt(ColumnSchema.CHARACTER_OCTET_LENGTH));
                column.setNumericPrecision(set.getInt(ColumnSchema.NUMERIC_PRECISION));
                column.setNumericScale(set.getInt(ColumnSchema.NUMERIC_SCALE));
                column.setCharacterSetName(set.getString(ColumnSchema.CHARACTER_SET_NAME));
                column.setCollationName(set.getString(ColumnSchema.COLLATION_NAME));
                column.setColumnType(set.getString(ColumnSchema.COLUMN_TYPE));
                column.setColumnKey(set.getString(ColumnSchema.COLUMN_KEY));
                column.setExtra(set.getString(ColumnSchema.EXTRA));
                column.setPrivileges(set.getString(ColumnSchema.PRIVILEGES));
                column.setColumnComment(set.getString(ColumnSchema.COLUMN_COMMENT));
                column.setGenerationExpressic(set.getString(ColumnSchema.GENERATION_EXPRESSIC));
                columns.add(column);
            }
            table.setColumnSchemas(columns.stream().sorted(Comparator.comparing(ColumnSchema::getColumnName)).collect(Collectors.toList()));
        }
    }


    /**
     * 初始化表的索引信息
     * @param dbInfo 数据库信息
     */
    private void initTableIndex(DBInfo dbInfo) throws SQLException {
        if(CollectionUtils.isEmpty(dbInfo.getTables())){
            return;
        }
        Connection conn = dbInfo.getConnection();
        List<MTable> tables = dbInfo.getTables();
        for(MTable table : tables){
            ResultSet set = conn.prepareStatement(MySQLCommonSql.getShowIndex(table.getTableName())).executeQuery();
            LinkedList<Index> indices = new LinkedList<>();
            while (set.next()){
                // 复合索引
                if(!CollectionUtils.isEmpty(indices) && indices.getLast().getIndexName().equals(set.getString(Index.KEY_NAME))){
                    Index index = indices.getLast();
                    index.setColumn(index.getColumn() + "," + set.getString(Index.COLUMN_NAME));
                }else{
                    // 普通索引
                    Index index = new Index();
                    index.setColumn(set.getString(Index.COLUMN_NAME));
                    index.setIndexType(IndexTypeEnum.getIndexType(set.getString(Index.INDEX_TYPE)));
                    index.setIndexName(set.getString(Index.KEY_NAME));
                    index.setIdxUniqueType(set.getInt(Index.NON_UNIQUE));
                    index.setTableName(table.getTableName());
                    indices.add(index);
                }
            }
            table.setIndices(indices.stream().sorted(Comparator.comparing(Index::getIndexName)).collect(Collectors.toList()));
        }
    }
}
