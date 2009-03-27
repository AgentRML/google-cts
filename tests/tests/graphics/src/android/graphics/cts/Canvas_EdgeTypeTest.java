/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.graphics.cts;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Canvas.EdgeType;
import android.test.AndroidTestCase;
import dalvik.annotation.TestTargets;
import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestTargetClass;

@TestTargetClass(Canvas.EdgeType.class)
public class Canvas_EdgeTypeTest extends AndroidTestCase {

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "test method: valueOf",
        method = "valueOf",
        args = {java.lang.String.class}
    )
    public void testValueOf(){
        assertEquals(EdgeType.BW, EdgeType.valueOf("BW"));
        assertEquals(EdgeType.AA, EdgeType.valueOf("AA"));
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "test method: values and tests for the behavior of the methods where this"
                    + "enumeration is used as an argument",
            method = "values",
            args = {}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "test method: values and tests for the behavior of the methods where this"
                    + "enumeration is used as an argument",
            method = "quickReject",
            args = {android.graphics.Path.class, android.graphics.Canvas.EdgeType.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "test method: values and tests for the behavior of the methods where this"
                    + "enumeration is used as an argument",
            method = "quickReject",
            args = {android.graphics.RectF.class, android.graphics.Canvas.EdgeType.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "test method: values and tests for the behavior of the methods where this"
                    + "enumeration is used as an argument",
            method = "quickReject",
            args = {int.class, int.class, int.class, int.class, 
                    android.graphics.Canvas.EdgeType.class}
        )
    })
    public void testValues(){
        EdgeType[] edgeType = EdgeType.values();

        assertEquals(2, edgeType.length);
        assertEquals(EdgeType.BW, edgeType[0]);
        assertEquals(EdgeType.AA, edgeType[1]);

        Canvas c = new Canvas();

        //EdgeType is used as a argument here for all the methods that use it
        c.quickReject(new Path(), EdgeType.AA);
        c.quickReject(new Path(), EdgeType.BW);
        c.quickReject(new RectF(), EdgeType.AA);
        c.quickReject(new RectF(), EdgeType.BW);
        c.quickReject(10, 100, 100, 10, EdgeType.AA);
        c.quickReject(10, 100, 100, 10, EdgeType.BW);
    }
}
