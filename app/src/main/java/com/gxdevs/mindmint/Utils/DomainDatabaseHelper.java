package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

public class DomainDatabaseHelper extends SQLiteOpenHelper {

	public static final String DB_NAME = "adult_domains.db";
	public static final int DB_VERSION = 1;

	public static final String TABLE_DOMAINS = "domains";
	public static final String COL_DOMAIN = "domains";

	public DomainDatabaseHelper(@NonNull Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DOMAINS + " (" + COL_DOMAIN + " TEXT PRIMARY KEY)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// No-op for v1
	}
}


