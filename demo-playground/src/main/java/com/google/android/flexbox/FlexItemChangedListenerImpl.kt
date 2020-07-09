/*
 * Copyright 2017 Google Inc. All rights reserved.
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

package com.google.android.flexbox

import android.view.ViewGroup

/**
 * Default implementation for the [FlexItemChangedListener].
 */
internal class FlexItemChangedListenerImpl(private val flexContainer: FlexContainer) : FlexItemChangedListener {

    override fun onFlexItemChanged(flexItem: FlexItem, viewIndex: Int) {
        // 把改变后的布局参数设置给对应的子 View.
        val view = flexContainer.getFlexItemAt(viewIndex)
        view.layoutParams = flexItem as ViewGroup.LayoutParams
    }
}
