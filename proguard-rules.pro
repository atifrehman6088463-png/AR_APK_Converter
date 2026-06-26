# Keep data models — Room and the print service both reflect on these fields
-keep class com.example.app.models.** { *; }

# Keep Room entities and DAOs from being stripped
-keep class com.example.app.data.db.entity.** { *; }
-keep class com.example.app.data.db.dao.** { *; }
-keep class com.example.app.data.db.** { *; }

# Keep ViewModel classes (referenced by ViewModelProvider via reflection)
-keep class com.example.app.ui.** { *; }

# Room: keep generated _Impl classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }

# Keep KhataPrintManager and inner adapter
-keep class com.example.app.utils.KhataPrintManager { *; }
