/**
 * Copyright (c) 2017-present, Future Corporation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package jp.co.future.uroborosql.mapping;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import jp.co.future.uroborosql.connection.ConnectionManager;
import jp.co.future.uroborosql.exception.UroborosqlRuntimeException;
import jp.co.future.uroborosql.utils.CaseFormat;

/**
 * テーブルメタ情報
 *
 * @author ota
 */
public interface TableMetadata {
	/**
	 * カラム情報
	 */
	interface Column {
		/**
		 * カラム名取得
		 *
		 * @return カラム名
		 */
		String getColumnName();

		/**
		 * カラム識別名取得
		 *
		 * @return カラム識別名
		 */
		default String getColumnIdentifier() {
			return getColumnName();
		}

		/**
		 * CamelCase カラム名取得
		 *
		 * @return CamelCase カラム名
		 */
		default String getCamelColumnName() {
			return CaseFormat.CAMEL_CASE.convert(getColumnName());
		}

		/**
		 * カラム型取得
		 *
		 * @return カラム型を表す値
		 */
		int getDataType();

		/**
		 * 主キー内の連番取得 (値1は主キーの最初の列、値2は主キーの2番目の列を表す)。
		 *
		 * @return 主キー内の連番
		 */
		int getKeySeq();

		/**
		 * 主キー判定
		 *
		 * @return true:主キー
		 */
		boolean isKey();

		/**
		 * コメント文字列取得
		 *
		 * @return コメント文字列
		 */
		String getRemarks();

		/**
		 * NULLが許可されるかどうかを取得
		 * @return NULLが許可されるかどうか
		 */
		boolean isNullable();

		int getOrdinalPosition();
	}

	/**
	 * テーブル名取得
	 *
	 * @return テーブル名
	 */
	String getTableName();

	/**
	 * テーブル名設定
	 *
	 * @param tableName テーブル名
	 */
	void setTableName(String tableName);

	/**
	 * スキーマ名取得
	 *
	 * @return スキーマ名
	 */
	String getSchema();

	/**
	 * スキーマ名設定
	 *
	 * @param schema スキーマ名
	 */
	void setSchema(String schema);

	/**
	 * SQL識別子を引用するのに使用する文字列を取得
	 *
	 * @return SQL識別子を引用するのに使用する文字列
	 */
	default String getIdentifierQuoteString() {
		return "\"";
	}

	/**
	 * SQL識別子を引用するのに使用する文字列を設定
	 *
	 * @param identifierQuoteString SQL識別子を引用するのに使用する文字列
	 */
	default void setIdentifierQuoteString(final String identifierQuoteString) {
		//noop
	}

	/**
	 * テーブル識別名の取得
	 *
	 * @return テーブル識別名
	 */
	default String getTableIdentifier() {
		String identifierQuoteString = getIdentifierQuoteString();
		if (StringUtils.isEmpty(identifierQuoteString)) {
			identifierQuoteString = "";
		}
		if (StringUtils.isEmpty(getSchema())) {
			return identifierQuoteString + getTableName() + identifierQuoteString;
		} else {
			return identifierQuoteString + getSchema() + identifierQuoteString + "." + identifierQuoteString
					+ getTableName() + identifierQuoteString;
		}
	}

	/**
	 * カラム取得
	 *
	 * @param camelColumnName カラムのキャメル名
	 * @return カラム
	 * @exception UroborosqlRuntimeException 指定したキャメルカラム名に該当するカラムが見つからなかった場合
	 */
	TableMetadata.Column getColumn(String camelColumnName);

	/**
	 * カラム取得
	 *
	 * @return カラム
	 */
	List<? extends TableMetadata.Column> getColumns();

	/**
	 * Keyカラム取得
	 *
	 * @return カラム
	 */
	default List<? extends TableMetadata.Column> getKeyColumns() {
		return getColumns().stream()
				.filter(TableMetadata.Column::isKey)
				.sorted(Comparator.comparingInt(TableMetadata.Column::getKeySeq))
				.collect(Collectors.toList());
	}

	/**
	 * テーブル名から、DatabaseMetaDataを利用して、TableMetadataの生成
	 *
	 * @param connectionManager コネクションマネージャー
	 * @param table テーブル情報
	 * @return TableMetadata
	 * @throws SQLException SQL例外
	 */
	static TableMetadata createTableEntityMetadata(final ConnectionManager connectionManager, final Table table)
			throws SQLException {

		Connection connection = connectionManager.getConnection();
		DatabaseMetaData metaData = connection.getMetaData();

		String schema = StringUtils.defaultIfEmpty(table.getSchema(), connection.getSchema());
		String tableName = table.getName();
		String identifierQuoteString = metaData.getIdentifierQuoteString();

		TableMetadataImpl entityMetadata = new TableMetadataImpl();

		Map<String, TableMetadataImpl.Column> columns = new HashMap<>();

		int tryCount = 0;//1回目：case変換なしで検索, 2回目：case変換後で検索
		while (tryCount < 2 && columns.isEmpty()) {
			tryCount++;
			if (tryCount == 2) {
				// case 変換
				if (metaData.storesLowerCaseIdentifiers()) {
					tableName = tableName.toLowerCase();
				} else if (metaData.storesUpperCaseIdentifiers()) {
					tableName = tableName.toUpperCase();
				}
				if (StringUtils.isNotEmpty(schema)) {
					if (metaData.storesLowerCaseIdentifiers()) {
						schema = schema.toLowerCase();
					} else if (metaData.storesUpperCaseIdentifiers()) {
						schema = schema.toUpperCase();
					}
				}
			}
			try (ResultSet rs = metaData.getColumns(null, StringUtils.isEmpty(schema) ? "%" : schema, tableName, "%")) {
				while (rs.next()) {
					String columnName = rs.getString("COLUMN_NAME");
					int sqlType = rs.getInt("DATA_TYPE");
					// If Types.DISTINCT like SQL DOMAIN, then get Source Date Type of SQL-DOMAIN
					if (sqlType == java.sql.Types.DISTINCT) {
						sqlType = rs.getInt("SOURCE_DATA_TYPE");
					}
					String remarks = rs.getString("REMARKS");
					String isNullable = rs.getString("IS_NULLABLE");
					int ordinalPosition = rs.getInt("ORDINAL_POSITION");

					TableMetadataImpl.Column column = new TableMetadataImpl.Column(columnName, sqlType, remarks,
							isNullable, ordinalPosition, identifierQuoteString);
					entityMetadata.addColumn(column);
					columns.put(column.getColumnName(), column);
				}
			}
		}
		entityMetadata.setSchema(schema);
		entityMetadata.setTableName(tableName);
		entityMetadata.setIdentifierQuoteString(identifierQuoteString);
		try (ResultSet rs = metaData.getPrimaryKeys(null, StringUtils.isEmpty(schema) ? "%" : schema, tableName)) {
			while (rs.next()) {
				String columnName = rs.getString(4);
				short keySeq = rs.getShort(5);
				columns.get(columnName).setKeySeq(keySeq);
			}
		}
		return entityMetadata;
	}
}
