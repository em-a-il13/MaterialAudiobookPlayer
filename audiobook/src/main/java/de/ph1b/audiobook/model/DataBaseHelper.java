package de.ph1b.audiobook.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.jcip.annotations.ThreadSafe;

import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.Iterator;
import java.util.List;

import de.ph1b.audiobook.utils.Communication;
import de.ph1b.audiobook.utils.L;

import static com.google.common.base.Preconditions.checkArgument;

@ThreadSafe
@SuppressWarnings("TryFinallyCanBeTryWithResources")
public class DataBaseHelper extends SQLiteOpenHelper {

    // book keys
    public static final String BOOK_ID = "bookId";
    public static final String BOOK_NAME = "bookName";
    public static final String BOOK_AUTHOR = "bookAuthor";
    public static final String BOOK_CURRENT_MEDIA_PATH = "bookCurrentMediaPath";
    public static final String BOOK_PLAYBACK_SPEED = "bookSpeed";
    public static final String BOOK_ROOT = "bookRoot";
    public static final String BOOK_TIME = "bookTime";
    public static final String BOOK_TYPE = "bookType";
    public static final String BOOK_USE_COVER_REPLACEMENT = "bookUseCoverReplacement";
    public static final String BOOK_ACTIVE = "BOOK_ACTIVE";
    public static final String CHAPTER_DURATION = "chapterDuration";
    public static final String CHAPTER_NAME = "chapterName";
    public static final String CHAPTER_PATH = "chapterPath";
    public static final String BOOKMARK_TIME = "bookmarkTime";
    public static final String BOOKMARK_PATH = "bookmarkPath";
    public static final String BOOKMARK_TITLE = "bookmarkTitle";
    private static final int DATABASE_VERSION = 31;
    private static final String DATABASE_NAME = "autoBookDB";
    private static final String TABLE_BOOK = "tableBooks";
    private static final String TABLE_CHAPTERS = "tableChapters";
    private static final String TABLE_BOOKMARKS = "tableBookmarks";
    private static final String CREATE_TABLE_BOOK = "CREATE TABLE " + TABLE_BOOK + " ( " +
            BOOK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            BOOK_NAME + " TEXT NOT NULL, " +
            BOOK_AUTHOR + " TEXT, " +
            BOOK_CURRENT_MEDIA_PATH + " TEXT NOT NULL, " +
            BOOK_PLAYBACK_SPEED + " REAL NOT NULL, " +
            BOOK_ROOT + " TEXT NOT NULL, " +
            BOOK_TIME + " INTEGER NOT NULL, " +
            BOOK_TYPE + " TEXT NOT NULL, " +
            BOOK_USE_COVER_REPLACEMENT + " INTEGER NOT NULL, " +
            BOOK_ACTIVE + " INTEGER NOT NULL DEFAULT 1)";

    private static final String CREATE_TABLE_CHAPTERS = "CREATE TABLE " + TABLE_CHAPTERS + " ( " +
            CHAPTER_DURATION + " INTEGER NOT NULL, " +
            CHAPTER_NAME + " TEXT NOT NULL, " +
            CHAPTER_PATH + " TEXT NOT NULL, " +
            BOOK_ID + " INTEGER NOT NULL, " +
            "FOREIGN KEY (" + BOOK_ID + ") REFERENCES " + TABLE_BOOK + "(" + BOOK_ID + "))";

    private static final String CREATE_TABLE_BOOKMARKS = "CREATE TABLE " + TABLE_BOOKMARKS + " ( " +
            BOOKMARK_PATH + " TEXT NOT NULL, " +
            BOOKMARK_TITLE + " TEXT NOT NULL, " +
            BOOKMARK_TIME + " INTEGER NOT NULL, " +
            BOOK_ID + " INTEGER NOT NULL, " +
            "FOREIGN KEY (" + BOOK_ID + ") REFERENCES " + TABLE_BOOK + "(" + BOOK_ID + "))";

    private static final String TAG = DataBaseHelper.class.getSimpleName();
    private static DataBaseHelper instance;
    private final Context c;
    private final List<Book> activeBooks = new ArrayList<>();
    private final List<Book> orphanedBooks = new ArrayList<>();
    private final Communication communication = Communication.getInstance();

    private DataBaseHelper(Context c) {
        super(c, DATABASE_NAME, null, DATABASE_VERSION);
        this.c = c;

        SQLiteDatabase db = getReadableDatabase();
        Cursor bookCursor = db.query(TABLE_BOOK,
                new String[]{BOOK_ID, BOOK_NAME, BOOK_AUTHOR, BOOK_CURRENT_MEDIA_PATH,
                        BOOK_PLAYBACK_SPEED, BOOK_ROOT, BOOK_TIME, BOOK_TYPE, BOOK_USE_COVER_REPLACEMENT,
                        BOOK_ACTIVE},
                null, null, null, null, null);
        try {
            while (bookCursor.moveToNext()) {
                long bookId = bookCursor.getLong(0);
                String bookName = bookCursor.getString(1);
                String bookAuthor = bookCursor.getString(2);
                String bookmarkCurrentMediaPath = bookCursor.getString(3);
                float bookSpeed = bookCursor.getFloat(4);
                String bookRoot = bookCursor.getString(5);
                int bookTime = bookCursor.getInt(6);
                Book.Type bookType = Book.Type.valueOf(bookCursor.getString(7));
                boolean bookUseCoverReplacement = bookCursor.getInt(8) == 1;
                boolean bookActive = bookCursor.getInt(9) == 1;

                List<Chapter> chapters = new ArrayList<>();
                Cursor chapterCursor = db.query(TABLE_CHAPTERS,
                        new String[]{CHAPTER_DURATION, CHAPTER_NAME, CHAPTER_PATH},
                        BOOK_ID + "=?",
                        new String[]{String.valueOf(bookId)},
                        null, null, null);
                try {
                    while (chapterCursor.moveToNext()) {
                        int chapterDuration = chapterCursor.getInt(0);
                        String chapterName = chapterCursor.getString(1);
                        String chapterPath = chapterCursor.getString(2);
                        chapters.add(new Chapter(chapterPath, chapterName, chapterDuration));
                    }
                } finally {
                    chapterCursor.close();
                }

                List<Bookmark> bookmarks = new ArrayList<>();
                Cursor bookmarkCursor = db.query(TABLE_BOOKMARKS,
                        new String[]{BOOKMARK_PATH, BOOKMARK_TIME, BOOKMARK_TITLE},
                        BOOK_ID + "=?", new String[]{String.valueOf(bookId)}
                        , null, null, null);
                try {
                    while (bookmarkCursor.moveToNext()) {
                        String bookmarkPath = bookmarkCursor.getString(0);
                        int bookmarkTime = bookmarkCursor.getInt(1);
                        String bookmarkTitle = bookmarkCursor.getString(2);
                        bookmarks.add(new Bookmark(bookmarkPath, bookmarkTitle, bookmarkTime));
                    }
                } finally {
                    bookmarkCursor.close();
                }

                Book book = new Book(bookRoot, bookName, bookAuthor, chapters,
                        bookmarkCurrentMediaPath, bookType, bookmarks, c);
                book.setPlaybackSpeed(bookSpeed);
                book.setPosition(bookTime, bookmarkCurrentMediaPath);
                book.setUseCoverReplacement(bookUseCoverReplacement);
                book.setId(bookId);

                if (bookActive) {
                    activeBooks.add(book);
                } else {
                    orphanedBooks.add(book);
                }
            }
        } finally {
            bookCursor.close();
        }
    }

    public static synchronized DataBaseHelper getInstance(Context c) {
        if (instance == null) {
            instance = new DataBaseHelper(c.getApplicationContext());
        }
        return instance;
    }


    public synchronized void addBook(@NonNull Book book) {
        L.v(TAG, "addBook=" + book.getName());
        checkArgument(!book.getChapters().isEmpty());

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues bookCv = book.getContentValues();

            long bookId = db.insert(TABLE_BOOK, null, bookCv);
            book.setId(bookId);

            for (Chapter c : book.getChapters()) {
                ContentValues chapterCv = c.getContentValues(book.getId());
                db.insert(TABLE_CHAPTERS, null, chapterCv);
            }

            for (Bookmark b : book.getBookmarks()) {
                ContentValues bookmarkCv = b.getContentValues(book.getId());
                db.insert(TABLE_BOOKMARKS, null, bookmarkCv);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        activeBooks.add(book);

        communication.bookSetChanged(activeBooks);
    }

    @Nullable
    public synchronized Book getBook(long id) {
        for (Book b : activeBooks) {
            if (b.getId() == id) {
                return new Book(b);
            }
        }
        return null;
    }


    @NonNull
    public synchronized List<Book> getActiveBooks() {
        List<Book> copyBooks = new ArrayList<>();
        for (Book b : activeBooks) {
            copyBooks.add(new Book(b));
        }
        return copyBooks;
    }

    @NonNull
    public synchronized List<Book> getOrphanedBooks() {
        List<Book> copyBooks = new ArrayList<>();
        for (Book b : orphanedBooks) {
            copyBooks.add(new Book(b));
        }
        return copyBooks;
    }

    public synchronized void updateBook(@NonNull Book book) {
        L.v(TAG, "updateBook=" + book.getName());
        checkArgument(!book.getChapters().isEmpty());

        int indexToUpdate = -1;
        for (int i = 0; i < activeBooks.size(); i++) {
            if (activeBooks.get(i).getId() == book.getId()) {
                indexToUpdate = i;
            }
        }
        if (indexToUpdate != -1) {
            activeBooks.set(indexToUpdate, book);

            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                // update book itself
                ContentValues bookCv = book.getContentValues();
                db.update(TABLE_BOOK, bookCv, BOOK_ID + "=?", new String[]{String.valueOf(book.getId())});

                // delete old chapters and replace them with new ones
                db.delete(TABLE_CHAPTERS, BOOK_ID + "=?", new String[]{String.valueOf(book.getId())});
                for (Chapter c : book.getChapters()) {
                    ContentValues chapterCv = c.getContentValues(book.getId());
                    db.insert(TABLE_CHAPTERS, null, chapterCv);
                }

                // replace old bookmarks and replace them with new ones
                db.delete(TABLE_BOOKMARKS, BOOK_ID + "=?", new String[]{String.valueOf(book.getId())});
                for (Bookmark b : book.getBookmarks()) {
                    ContentValues bookmarkCV = b.getContentValues(book.getId());
                    db.insert(TABLE_BOOKMARKS, null, bookmarkCV);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            communication.sendBookContentChanged(book);
        } else {
            L.e(TAG, "Could not update book=" + book);
        }
    }

    public synchronized void hideBook(@NonNull Book book) {
        L.v(TAG, "hideBook=" + book.getName());
        checkArgument(!book.getChapters().isEmpty());

        int indexToHide = -1;
        for (int i = 0; i < activeBooks.size(); i++) {
            if (activeBooks.get(i).getId() == book.getId()) {
                indexToHide = i;
            }
        }
        if (indexToHide == -1) {
            throw new AssertionError("This should not have happened. Tried to remove a not existing book");
        } else {
            activeBooks.remove(indexToHide);
            orphanedBooks.add(book);

            ContentValues cv = new ContentValues();
            cv.put(BOOK_ACTIVE, 0);
            getWritableDatabase().update(TABLE_BOOK, cv, BOOK_ID + "=?", new String[]{String.valueOf(book.getId())});

            communication.bookSetChanged(activeBooks);
        }
    }

    public synchronized void revealBook(@NonNull Book book) {
        checkArgument(!book.getChapters().isEmpty());

        Iterator<Book> orphanedBookIterator = orphanedBooks.iterator();
        while (orphanedBookIterator.hasNext()) {
            if (orphanedBookIterator.next().getId() == book.getId()) {
                orphanedBookIterator.remove();
                break;
            }
        }
        activeBooks.add(book);
        ContentValues cv = new ContentValues();
        cv.put(BOOK_ACTIVE, 1);
        getWritableDatabase().update(TABLE_BOOK, cv, BOOK_ID + "=?", new String[]{String.valueOf(book.getId())});

        communication.bookSetChanged(activeBooks);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_BOOK);
        db.execSQL(CREATE_TABLE_CHAPTERS);
        db.execSQL(CREATE_TABLE_BOOKMARKS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            DataBaseUpgradeHelper upgradeHelper = new DataBaseUpgradeHelper(db, c);
            upgradeHelper.upgrade(oldVersion);
        } catch (InvalidPropertiesFormatException e) {
            L.e(TAG, "Error at upgrade", e);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOK);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAPTERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
            onCreate(db);
        }
    }
}
