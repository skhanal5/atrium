package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * A search field the remote can reach.
 *
 * At rest it is one more focusable item in the grid. Select hands it to the
 * Fire TV keyboard. While the keyboard is up, keys go to it, not to the grid.
 * Done or Back gives the remote back to the grid.
 */
public final class SearchBox extends FrameLayout implements Focusable {

    public interface Listener {
        /** Each time the text changes. */
        void onSearch(String text);

        /** The keyboard was closed with Done. */
        void onDone();
    }

    private final EditText edit;
    private final GradientDrawable bg = new GradientDrawable();
    private Listener listener;
    private boolean focused;

    public SearchBox(Context context) {
        super(context);
        bg.setCornerRadius(Dp.f(9f));
        setBackground(bg);
        setPadding(Dp.of(14f), 0, Dp.of(14f), 0);

        edit = new EditText(context);
        edit.setBackground(null);
        edit.setTypeface(Type.medium());
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(13f));
        edit.setHint("Search, like Spotify, ESPN or music");
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setPadding(0, 0, 0, 0);
        // Only focusable while typing, so Android never moves focus here on its own.
        edit.setFocusable(false);
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (listener != null) {
                    listener.onSearch(s.toString());
                }
            }
        });
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && listener != null) {
                listener.onDone();
                return true;
            }
            return false;
        });
        addView(edit, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
        style();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public String text() {
        return edit.getText().toString();
    }

    /** True while the keyboard has the field. */
    public boolean isEditing() {
        return edit.hasFocus();
    }

    /** Give the field to the keyboard. */
    @Override
    public void activate() {
        edit.setFocusable(true);
        edit.setFocusableInTouchMode(true);
        edit.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Take the field back from the keyboard. */
    public void stopEditing() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        }
        edit.clearFocus();
        edit.setFocusable(false);
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        style();
        Scale.to(this, f ? 1.03f : 1f);
    }

    private void style() {
        bg.setColor(focused ? Colors.TEXT : Colors.SURFACE);
        edit.setTextColor(focused ? Colors.INK : Colors.TEXT);
        edit.setHintTextColor(focused ? 0x8C0A0D14 : Colors.TEXT_3);
    }
}
