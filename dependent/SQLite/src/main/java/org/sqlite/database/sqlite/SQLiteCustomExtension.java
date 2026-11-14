/*
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
*/

package org.sqlite.database.sqlite;

/**
 * Describes a loadable extension to be loaded into every connection.
 *
 * @see SQLiteDatabase#loadExtension(String, String)
 */
public final class SQLiteCustomExtension {
    public final String file;
    public final String entryPoint;

    /**
     * Create custom extension.
     *
     * @param file The name of the shared library containing the extension,
     * as passed to sqlite3_load_extension().
     * @param entryPoint The extension's entry point, or null to let SQLite
     * derive it from the file name.
     */
    public SQLiteCustomExtension(String file, String entryPoint) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null.");
        }

        this.file = file;
        this.entryPoint = entryPoint;
    }
}
