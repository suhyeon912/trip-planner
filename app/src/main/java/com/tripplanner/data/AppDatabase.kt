package com.tripplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trip::class, Flight::class, Lodging::class, Place::class],
    version = 4,   // 구조가 바뀌면 1씩 올리고 아래 MIGRATION 추가
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    companion object {
        /**
         * 2 → 3: 출발 시각·공유 정보(여행), 카테고리·사진(장소), 교통 종류(교통편) 추가.
         * 기존 데이터는 그대로 두고 칸만 늘림 (기본값: 10:00 출발, 관광, 비행기).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN startMinutes INTEGER NOT NULL DEFAULT 600")
                db.execSQL("ALTER TABLE trips ADD COLUMN shareCode TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE places ADD COLUMN category TEXT NOT NULL DEFAULT 'SIGHT'")
                db.execSQL("ALTER TABLE places ADD COLUMN photoUri TEXT")
                db.execSQL("ALTER TABLE flights ADD COLUMN mode TEXT NOT NULL DEFAULT 'FLIGHT'")
            }
        }

        /** 3 → 4: 장소·숙소에 주소 · 상세 주소 · 위치 메모 추가 (기존 데이터는 빈 글자) */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("places", "lodgings")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN addressDetail TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN locationMemo TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        @Volatile private var instance: AppDatabase? = null

        /** 앱 전체에서 DB 는 하나만 만들어 씁니다 (싱글톤) */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext,
                    AppDatabase::class.java, "trip_planner.db")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // 그보다 오래된 개발용 버전(1)은 지우고 새로 만듦
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { instance = it }
            }
    }
}
