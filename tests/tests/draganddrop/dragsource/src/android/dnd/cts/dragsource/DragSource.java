/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.dnd.cts.dragsource;

import android.app.Activity;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

public class DragSource extends Activity{
    private static final String URI_PREFIX =
            "content://" + DragSourceContentProvider.AUTHORITY + "/data";

    private static final String MAGIC_VALUE = "42";
    public static final long TIMEOUT_CANCEL = 150;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        final Uri plainUri = Uri.parse(URI_PREFIX + "/" + MAGIC_VALUE);

        setUpDragSource(R.id.disallow_global, plainUri, 0);
        setUpDragSource(R.id.cancel_soon, plainUri, View.DRAG_FLAG_GLOBAL);

        setUpDragSource(R.id.dont_grant, plainUri, View.DRAG_FLAG_GLOBAL);
        setUpDragSource(R.id.grant_read, plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ);
        setUpDragSource(R.id.grant_write, plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_WRITE);
        setUpDragSource(R.id.grant_read_persistable, plainUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ |
                        View.DRAG_FLAG_GLOBAL_PERSISTABLE_URI_PERMISSION);

        final Uri prefixUri = Uri.parse(URI_PREFIX);

        setUpDragSource(R.id.grant_read_prefix, prefixUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ |
                        View.DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION);
        setUpDragSource(R.id.grant_read_noprefix, prefixUri,
                View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ);

    }

    private void setUpDragSource(final int resourceId, final Uri uri, final int flags) {
        findViewById(resourceId).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                v.startDragAndDrop(
                        ClipData.newUri(getContentResolver(), "", uri),
                        new View.DragShadowBuilder(v),
                        null,
                        flags);
                if (resourceId == R.id.cancel_soon) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.cancelDragAndDrop();
                        }
                    }, TIMEOUT_CANCEL);
                }
                return false;
            }
        });
    }
}
