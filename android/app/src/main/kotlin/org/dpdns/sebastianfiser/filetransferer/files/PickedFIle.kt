package org.dpdns.sebastianfiser.filetransferer.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

data calss PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)

fun queryFile(resolver: ContentResolver, uri: Uri): PickedFile? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OenableColumn.SIZE)
    resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null

        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (nameIdx < 0 || sizeIdx < 0 || cursor.isNull(sizeIdx)) return null

        return PickedFile(
            uri = uri,
            name = cursor.getString(nameIdx),
            size = cursor.getLong(sizeIdx)
        )
    }
    return null
}
