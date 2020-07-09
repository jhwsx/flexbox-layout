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

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.google.android.apps.flexbox.R
import com.google.android.flexbox.validators.*
import com.google.android.material.textfield.TextInputLayout

/**
 * DialogFragment that changes the properties for a flex item.
 * 这个页面设置的是给子 View 的布局参数，都是以 layout_ 开头的
 */
internal class FlexItemEditFragment : DialogFragment() {

    private lateinit var alignSelfAuto: String

    private lateinit var alignSelfFlexStart: String

    private lateinit var alignSelfFlexEnd: String

    private lateinit var alignSelfCenter: String

    private lateinit var alignSelfBaseline: String

    private lateinit var alignSelfStretch: String

    private var viewIndex: Int = 0

    private lateinit var flexItem: FlexItem

    /**
     * Instance of a [FlexItem] being edited. At first it's created as another instance from
     * the [flexItem] because otherwise changes before clicking the ok button will be
     * reflected if the [flexItem] is changed directly.
     */
    private lateinit var flexItemInEdit: FlexItem

    private var flexItemChangedListener: FlexItemChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置 Dialog 的样式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog)
        } else {
            setStyle(STYLE_NORMAL, android.R.style.Theme_Dialog)
        }
        arguments?.let {
            flexItem = it.getParcelable(FLEX_ITEM_KEY)!!
            viewIndex = it.getInt(VIEW_INDEX_KEY)
        }
        flexItemInEdit = createNewFlexItem(flexItem)

        activity?.let {
            alignSelfAuto = it.getString(R.string.auto)
            alignSelfFlexStart = it.getString(R.string.flex_start)
            alignSelfFlexEnd = it.getString(R.string.flex_end)
            alignSelfCenter = it.getString(R.string.center)
            alignSelfBaseline = it.getString(R.string.baseline)
            alignSelfStretch = it.getString(R.string.stretch)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_flex_item_edit, container, false)
        dialog?.setTitle((viewIndex + 1).toString())

        // layout_order: 编辑的是顺序, 是个 int 值, 可以是负数, 大的顺序在后, 小的顺序在前. 如果值都一样,子 View 按照在 xml 中出现的顺序来布局.
        // 这个设置在 FlexboxLayoutManager 中没有.
        val context = activity ?: return view
        val orderTextInput: TextInputLayout = view.findViewById(R.id.input_layout_order)
        val orderEdit: EditText = view.findViewById(R.id.edit_text_order)
        orderEdit.setText(flexItem.order.toString())
        orderEdit.addTextChangedListener(
                FlexEditTextWatcher(context, orderTextInput, IntegerInputValidator(),
                        R.string.must_be_integer))
        if (flexItem is FlexboxLayoutManager.LayoutParams) {
            // Order is not enabled in FlexboxLayoutManager
            orderEdit.isEnabled = false
        }

        // 编辑 layout_flexGrow (float): 设置子 View 如何分配可用空间,没有可用空间的话,设置无效
        val flexGrowInput: TextInputLayout = view .findViewById(R.id.input_layout_flex_grow)
        val flexGrowEdit: EditText = view.findViewById(R.id.edit_text_flex_grow)
        flexGrowEdit.setText(flexItem.flexGrow.toString())
        flexGrowEdit.addTextChangedListener(
                FlexEditTextWatcher(context, flexGrowInput, NonNegativeDecimalInputValidator(),
                        R.string.must_be_non_negative_float))

        // layout_flexShrink (float): 属性指定了 flex 元素的收缩规则。flex 元素仅在默认宽度之和大于容器的时候才会发生收缩，
        // 其收缩的大小是依据 flexShrink 的值, 这个值必须是正的.
        // 测试的时候先设置 Flex Wrap 为 Nowrap, 也就是不换行; 再把 flexShrink 的默认值改为正的
        // 当有新的子 View 加入时, 为了更新的子 View 腾出空间, 这个空间就按照收缩规则来分配, 谁的 flexShrink 的值大, 就收缩的多,
        // 谁的 flexShrink 的值小, 就收缩的少, 为 0 表示不收缩.
        val flexShrinkInput: TextInputLayout = view.findViewById(R.id.input_layout_flex_shrink)
        val flexShrinkEdit: EditText = view.findViewById(R.id.edit_text_flex_shrink)
        flexShrinkEdit.setText(flexItem.flexShrink.toString())
        flexShrinkEdit.addTextChangedListener(
                FlexEditTextWatcher(context, flexShrinkInput, NonNegativeDecimalInputValidator(),
                        R.string.must_be_non_negative_float))

        // layout_flexBasisPercent (fraction), 取值是 -1 或者非负的整数
        // 定义了相对于父 View 的百分数格式的子 View 初始化宽度. 子 View 的初始化主轴上的尺寸会尝试和指定的相对于父 View 主轴上的尺寸的百分数一样宽.
        // 这个值被指定后, 这个属性计算得到的值就会覆盖由 layout_width 或者 layout_height 指定的值
        // layout_flexBasisPercent 属性定义了在分配多余空间之前，子元素占据的主轴空间的百分比。它的默认值为auto，即子元素的本来大小。
        // layout_flexBasisPercent 仅仅当父 View 的长度是确定的(也就是说 MeasureSpec 的模式是 MeasureSpec.EXACTLY)
        // 默认值是 -1, 表示没有设置
        val flexBasisPercentInput: TextInputLayout =
                view.findViewById(R.id.input_layout_flex_basis_percent)
        val flexBasisPercentEdit: EditText = view.findViewById(R.id.edit_text_flex_basis_percent)
        if (flexItem.flexBasisPercent != FlexboxLayout.LayoutParams.FLEX_BASIS_PERCENT_DEFAULT) {
            flexBasisPercentEdit
                    .setText(Math.round(flexItem.flexBasisPercent * 100).toString())
        } else {
            flexBasisPercentEdit.setText(flexItem.flexBasisPercent.toInt().toString())
        }
        flexBasisPercentEdit.addTextChangedListener(
                FlexEditTextWatcher(context, flexBasisPercentInput, FlexBasisPercentInputValidator(),
                        R.string.must_be_minus_one_or_non_negative_integer))
        // layout_width: 设置子 View 的宽度
        val widthInput: TextInputLayout = view.findViewById(R.id.input_layout_width)
        val widthEdit: EditText = view.findViewById(R.id.edit_text_width)
        widthEdit.setText(context.pixelToDp(flexItem.width).toString())
        widthEdit.addTextChangedListener(
                FlexEditTextWatcher(context, widthInput, DimensionInputValidator(),
                        R.string.must_be_minus_one_or_minus_two_or_non_negative_integer))
        // layout_height: 设置子 View 的高度
        val heightInput: TextInputLayout = view.findViewById(R.id.input_layout_height)
        val heightEdit: EditText= view.findViewById(R.id.edit_text_height)
        heightEdit.setText(context.pixelToDp(flexItem.height).toString())
        heightEdit.addTextChangedListener(
                FlexEditTextWatcher(context, heightInput, DimensionInputValidator(),
                        R.string.must_be_minus_one_or_minus_two_or_non_negative_integer))
        // layout_minWidth (dimension) 最小宽度
        // 这个属性给子 View 添加最小宽度的约束。
        val minWidthInput: TextInputLayout = view.findViewById(R.id.input_layout_min_width)
        val minWidthEdit: EditText = view.findViewById(R.id.edit_text_min_width)
        minWidthEdit.setText(context.pixelToDp(flexItem.minWidth).toString())
        minWidthEdit.addTextChangedListener(
                FlexEditTextWatcher(context, minWidthInput, FixedDimensionInputValidator(),
                        R.string.must_be_non_negative_integer))
        // layout_minHeight (dimension)
        val minHeightInput: TextInputLayout = view.findViewById(R.id.input_layout_min_height)
        val minHeightEdit: EditText = view.findViewById(R.id.edit_text_min_height)
        minHeightEdit.setText(context.pixelToDp(flexItem.minHeight).toString())
        minHeightEdit.addTextChangedListener(
                FlexEditTextWatcher(context, minHeightInput, FixedDimensionInputValidator(),
                        R.string.must_be_non_negative_integer))
        // layout_maxWidth (dimension)
        val maxWidthInput: TextInputLayout = view.findViewById(R.id.input_layout_max_width)
        val maxWidthEdit: EditText = view.findViewById(R.id.edit_text_max_width)
        maxWidthEdit.setText(context.pixelToDp(flexItem.maxWidth).toString())
        maxWidthEdit.addTextChangedListener(
                FlexEditTextWatcher(context, maxWidthInput, FixedDimensionInputValidator(),
                        R.string.must_be_non_negative_integer))
        // layout_maxHeight (dimension)
        val maxHeightInput: TextInputLayout = view.findViewById(R.id.input_layout_max_height)
        val maxHeightEdit: EditText = view.findViewById(R.id.edit_text_max_height)
        maxHeightEdit.setText(context.pixelToDp(flexItem.maxHeight).toString())
        maxHeightEdit.addTextChangedListener(
                FlexEditTextWatcher(context, maxHeightInput, FixedDimensionInputValidator(),
                        R.string.must_be_non_negative_integer))

        setNextFocusesOnEnterDown(orderEdit, flexGrowEdit, flexShrinkEdit, flexBasisPercentEdit,
                widthEdit, heightEdit, minWidthEdit, minHeightEdit, maxWidthEdit, maxHeightEdit)
        // layout_alignSelf
        // 子 View 在相交轴上如何对齐： 允许单个子 View 单独设置对齐方式，可以覆盖父 View 设置的 alignItems 属性。
        val alignSelfSpinner: Spinner = view.findViewById(R.id.spinner_align_self)
        val arrayAdapter = ArrayAdapter.createFromResource(requireActivity(),
                R.array.array_align_self, R.layout.spinner_item)
        alignSelfSpinner.adapter = arrayAdapter
        alignSelfSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, ignored: View?, position: Int,
                                        id: Long) {
                flexItemInEdit.alignSelf = when (parent.getItemAtPosition(position).toString()) {
                    alignSelfAuto -> AlignSelf.AUTO
                    alignSelfFlexStart -> AlignItems.FLEX_START
                    alignSelfFlexEnd -> AlignItems.FLEX_END
                    alignSelfCenter -> AlignItems.CENTER
                    alignSelfBaseline -> AlignItems.BASELINE
                    alignSelfStretch -> AlignItems.STRETCH
                    else -> return
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No op
            }
        }
        // layout_wrapBefore (boolean)
        // 如果设置一个子 View 的这个属性为 true，那么这个子 View 将成为一个 flex line 的第一个元素。
        // 如果 flex_wrap 属性设置为 noWrap，那么这个属性将会被忽略掉。
        val wrapBeforeCheckBox: CheckBox = view.findViewById(R.id.checkbox_wrap_before)
        wrapBeforeCheckBox.isChecked = flexItem.isWrapBefore
        wrapBeforeCheckBox.setOnCheckedChangeListener { _, isChecked ->
            flexItemInEdit.isWrapBefore = isChecked }
        val alignSelfPosition = arrayAdapter
                .getPosition(alignSelfAsString(flexItem.alignSelf))
        alignSelfSpinner.setSelection(alignSelfPosition)

        view.findViewById<Button>(R.id.button_cancel).setOnClickListener {
            copyFlexItemValues(flexItem, flexItemInEdit)
            dismiss()
        }
        val okButton: Button = view.findViewById(R.id.button_ok)
        okButton.setOnClickListener(View.OnClickListener {
            if (orderTextInput.isErrorEnabled || flexGrowInput.isErrorEnabled ||
                    flexBasisPercentInput.isErrorEnabled || widthInput.isErrorEnabled ||
                    heightInput.isErrorEnabled || minWidthInput.isErrorEnabled ||
                    minHeightInput.isErrorEnabled || maxWidthInput.isErrorEnabled ||
                    maxHeightInput.isErrorEnabled) {
                Toast.makeText(activity, R.string.invalid_values_exist, Toast.LENGTH_SHORT)
                        .show()
                return@OnClickListener
            }
            if (flexItemChangedListener != null) {
                // 把编辑后的布局参数的内容值复制给传入的 flexItem, 并回调出去
                copyFlexItemValues(flexItemInEdit, flexItem)
                flexItemChangedListener!!.onFlexItemChanged(flexItem, viewIndex)
            }
            dismiss()
        })
        return view
    }

    fun setFlexItemChangedListener(flexItemChangedListener: FlexItemChangedListener) {
        this.flexItemChangedListener = flexItemChangedListener
    }
    // 让光标在控件间移动
    private fun setNextFocusesOnEnterDown(vararg textViews: TextView) {
        // This can be done by setting android:nextFocus* as in
        // https://developer.android.com/training/keyboard-input/navigation.html
        // But it requires API level 11 as a minimum sdk version. To support the lower level
        // devices,
        // doing it programmatically.
        for (i in textViews.indices) {
            textViews[i].setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_NEXT ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_NULL
                                && event.action == KeyEvent.ACTION_DOWN
                                && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (i + 1 < textViews.size) {
                        // 下一个控件获取焦点
                        textViews[i + 1].requestFocus()
                    } else if (i == textViews.size - 1) {
                        // 如果是最后一个控件，就隐藏软键盘
                        val inputMethodManager = activity?.getSystemService(
                                Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                        inputMethodManager?.hideSoftInputFromWindow(v.windowToken, 0)
                    }
                }
                true
            }

            // Suppress the key focus change by KeyEvent.ACTION_UP of the enter key
            textViews[i].setOnKeyListener { _, keyCode, event -> keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP }
        }

    }

    private fun alignSelfAsString(alignSelf: Int): String {
        return when (alignSelf) {
            AlignSelf.AUTO -> alignSelfAuto
            AlignItems.FLEX_START -> alignSelfFlexStart
            AlignItems.FLEX_END -> alignSelfFlexEnd
            AlignItems.CENTER -> alignSelfCenter
            AlignItems.BASELINE -> alignSelfBaseline
            AlignItems.STRETCH -> alignSelfStretch
            else -> alignSelfAuto
        }
    }

    private inner class FlexEditTextWatcher internal constructor(val context: Context,
                                                                 val textInputLayout: TextInputLayout,
                                                                 val inputValidator: InputValidator,
                                                                 val errorMessageId: Int) : TextWatcher {

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // No op
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (inputValidator.isValidInput(s)) {
                textInputLayout.isErrorEnabled = false
                textInputLayout.error = ""
            } else {
                textInputLayout.isErrorEnabled = true
                textInputLayout.error = activity?.resources?.getString(errorMessageId)
            }
        }

        override fun afterTextChanged(editable: Editable) {
            if (textInputLayout.isErrorEnabled || editable.isEmpty() ||
                    !inputValidator.isValidInput(editable.toString())) {
                return
            }
            val value = editable.toString().toFloatOrNull() ?: return
            when (textInputLayout.id) {
                R.id.input_layout_order -> if (flexItemInEdit !is FlexboxLayoutManager.LayoutParams) {
                    flexItemInEdit.order = value.toInt()
                } else return
                R.id.input_layout_flex_grow -> flexItemInEdit.flexGrow = value
                R.id.input_layout_flex_shrink -> flexItemInEdit.flexShrink = value
                R.id.input_layout_width -> flexItemInEdit.width = context.dpToPixel(value.toInt())
                R.id.input_layout_height -> flexItemInEdit.height = context.dpToPixel(value.toInt())
                R.id.input_layout_flex_basis_percent -> if (value != FlexboxLayout.LayoutParams.FLEX_BASIS_PERCENT_DEFAULT) {
                    flexItemInEdit.flexBasisPercent = value.toInt() / 100.0f
                } else {
                    flexItemInEdit.flexBasisPercent = FlexItem.FLEX_BASIS_PERCENT_DEFAULT
                }
                R.id.input_layout_min_width -> flexItemInEdit.minWidth = context.dpToPixel(value.toInt())
                R.id.input_layout_min_height -> flexItemInEdit.minHeight = context.dpToPixel(value.toInt())
                R.id.input_layout_max_width -> flexItemInEdit.maxWidth = context.dpToPixel(value.toInt())
                R.id.input_layout_max_height -> flexItemInEdit.maxHeight = context.dpToPixel(value.toInt())
                else -> return
            }
        }
    }
    /** 创建 FlexItem 的副本，用于编辑 */
    private fun createNewFlexItem(item: FlexItem): FlexItem {
        if (item is FlexboxLayout.LayoutParams) {
            val newItem = FlexboxLayout.LayoutParams(item.getWidth(), item.getHeight())
            copyFlexItemValues(item, newItem)
            return newItem
        } else if (item is FlexboxLayoutManager.LayoutParams) {
            val newItem = FlexboxLayoutManager.LayoutParams(item.getWidth(), item.getHeight())
            copyFlexItemValues(item, newItem)
            return newItem
        }
        throw IllegalArgumentException("Unknown FlexItem: $item")
    }

    private fun copyFlexItemValues(from: FlexItem, to: FlexItem) {
        if (from !is FlexboxLayoutManager.LayoutParams) {
            to.order = from.order
        }
        to.flexGrow = from.flexGrow
        to.flexShrink = from.flexShrink
        to.flexBasisPercent = from.flexBasisPercent
        to.height = from.height
        to.width = from.width
        to.maxHeight = from.maxHeight
        to.minHeight = from.minHeight
        to.maxWidth = from.maxWidth
        to.minWidth = from.minWidth
        to.alignSelf = from.alignSelf
        to.isWrapBefore = from.isWrapBefore
    }

    companion object {

        private const val FLEX_ITEM_KEY = "flex_item"

        private const val VIEW_INDEX_KEY = "view_index"

        /**
         * 参数一：布局参数
         * 参数二：索引
         */
        fun newInstance(flexItem: FlexItem, viewIndex: Int) = FlexItemEditFragment().apply {
            arguments = Bundle().apply {
                putParcelable(FLEX_ITEM_KEY, flexItem)
                putInt(VIEW_INDEX_KEY, viewIndex)
            }
        }
    }
}
