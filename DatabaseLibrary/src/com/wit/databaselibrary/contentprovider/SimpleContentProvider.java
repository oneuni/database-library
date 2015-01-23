package com.wit.databaselibrary.contentprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import com.wit.databaselibrary.contentprovider.databaseinfo.DatabaseInfo;

public abstract class SimpleContentProvider extends ContentProvider {
	public static abstract class DatabaseBaseColumns implements BaseColumns {
		public static final String ID = "_id";

		public String getContentType() {
			final String tableName = this.getTableName();
			final String contentType =
					"vnd.android.cursor.dir/vnd.wit." + tableName;

			return contentType;
		}

		public abstract String getTableName();
	}

	protected static final List<DatabaseInfo> DATABASE_INFOS =
			new ArrayList<DatabaseInfo>();

	private String adjustSelection( final Uri uri, String selection ) {
		for ( final DatabaseInfo databaseInfo : SimpleContentProvider.DATABASE_INFOS ) {
			if ( databaseInfo.uriMatchesObjectId( uri ) ) {
				selection = databaseInfo.addSelectionById( uri, selection );

				break;
			}
		}

		return selection;
	}

	@Override
	public int delete( final Uri uri, final String selection,
			final String[] selectionArgs ) {
		final String tableName = this.getTableName( uri );
		final String newSelection = this.adjustSelection( uri, selection );
		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();
		final int count =
				sqLiteDatabase.delete( tableName, newSelection, selectionArgs );

		this.getContext().getContentResolver().notifyChange( uri, null );

		return count;
	}

	protected abstract SQLiteOpenHelper getDatabaseHelper();

	protected abstract String getDatabaseName();

	protected abstract int getDatabaseVersion();

	private String getTableName( final Uri uri ) {
		String tableName = null;

		for ( final DatabaseInfo databaseInfo : SimpleContentProvider.DATABASE_INFOS ) {
			if ( databaseInfo.uriMatches( uri ) ) {
				tableName = databaseInfo.getTableName();

				break;
			}
		}

		if ( tableName == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		return tableName;
	}

	@Override
	public String getType( final Uri uri ) {
		DatabaseBaseColumns databaseBaseColumns = null;

		for ( final DatabaseInfo databaseInfo : SimpleContentProvider.DATABASE_INFOS ) {
			if ( databaseInfo.uriMatchesObject( uri ) ) {
				databaseBaseColumns = databaseInfo.getColumns();
			}
		}

		if ( databaseBaseColumns == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		final String contentType = databaseBaseColumns.getContentType();

		return contentType;
	}

	@Override
	public Uri insert( final Uri uri, ContentValues contentValues ) {
		DatabaseInfo databaseInfo = null;

		for ( final DatabaseInfo currentDatabaseInfo : SimpleContentProvider.DATABASE_INFOS ) {
			if ( currentDatabaseInfo.uriMatchesObject( uri ) ) {
				databaseInfo = currentDatabaseInfo;
			}
		}

		if ( databaseInfo == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();

		if ( contentValues == null ) {
			contentValues = new ContentValues();
		}

		final DatabaseBaseColumns databaseBaseColumns =
				databaseInfo.getColumns();
		final String tableName = databaseBaseColumns.getTableName();
		final Uri contentUri = databaseInfo.getContentUri();
		final String nullColumnHack;

		if ( contentValues.size() == 0 ) {
			nullColumnHack = DatabaseBaseColumns.ID;
		} else {
			nullColumnHack = null;
		}

		final long rowId =
				sqLiteDatabase.insert( tableName, nullColumnHack, contentValues );

		if ( rowId > 0 ) {
			final Uri contentUriWithAppendedId =
					ContentUris.withAppendedId( contentUri, rowId );

			this.getContext().getContentResolver().notifyChange(
					contentUriWithAppendedId, null );

			return contentUriWithAppendedId;
		} else {
			throw new SQLException( "Failed to insert row into " + uri );
		}
	}

	@Override
	public Cursor query( final Uri uri, final String[] projection,
			final String selection, final String[] selectionArgs,
			final String sortOrder ) {
		Map<String, String> projectionMap = null;

		for ( final DatabaseInfo databaseInfo : SimpleContentProvider.DATABASE_INFOS ) {
			if ( databaseInfo.uriMatches( uri ) ) {
				projectionMap = databaseInfo.getProjectionMap();
			}
		}

		if ( projectionMap == null ) {
			throw new IllegalArgumentException( "Unknown URI: " + uri );
		}

		String newSelection;

		if ( selection == null ) {
			newSelection = this.adjustSelection( uri, "" );
		} else {
			newSelection = this.adjustSelection( uri, selection );
		}

		final SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
		final String tableName = this.getTableName( uri );

		sqLiteQueryBuilder.setTables( tableName );
		sqLiteQueryBuilder.setProjectionMap( projectionMap );

		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getReadableDatabase();
		final Cursor cursor =
				sqLiteQueryBuilder.query( sqLiteDatabase, projection,
						newSelection, selectionArgs, null, null, sortOrder );

		cursor.setNotificationUri( this.getContext().getContentResolver(), uri );

		return cursor;
	}

	@Override
	public int update( final Uri uri, final ContentValues contentValues,
			final String selection, final String[] selectionArgs ) {
		final SQLiteOpenHelper databaseHelper = this.getDatabaseHelper();
		final SQLiteDatabase sqLiteDatabase =
				databaseHelper.getWritableDatabase();
		final String tableName = this.getTableName( uri );

		final int count =
				sqLiteDatabase.update( tableName, contentValues, selection,
						selectionArgs );

		this.getContext().getContentResolver().notifyChange( uri, null );

		return count;
	}
}