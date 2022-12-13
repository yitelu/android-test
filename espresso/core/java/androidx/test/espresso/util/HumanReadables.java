/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.test.espresso.util;

import static androidx.test.espresso.util.TreeIterables.depthFirstViewTraversalWithDistance;
import static androidx.test.internal.util.Checks.checkArgument;
import static java.lang.Math.max;

import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.util.Printer;
import android.util.StringBuilderPrinter;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Checkable;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.collections.CollectionsKt;
import kotlin.text.StringsKt;

/** Text converters for various Android objects. */
public final class HumanReadables {

  /** Regex that matches a valid Java identifier. */
  private static final String JAVA_ID = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

  /**
   * Pattern that matches a result from the default Object.toString method; that is, a
   * fully-qualified Java class name, an at sign ("@"), and an unsigned hexadecimal hash code. In a
   * match, group 2 will be the subsequence containing the hexadecimal value.
   */
  private static final Pattern OBJECT_HASH_CODE_PATTERN =
      Pattern.compile(JAVA_ID + "(\\." + JAVA_ID + ")*@([0-9A-Fa-f]+)");

  private HumanReadables() {}

  /**
   * Prints out an error message featuring the view hierarchy starting at the rootView.
   *
   * @param rootView the root of the hierarchy tree to print out.
   * @param problemViews list of the views that you would like to point out are causing the error
   *     message or null, if you want to skip this feature.
   * @param errorHeader the header of the error message (should contain the description of why the
   *     error is happening).
   * @param problemViewSuffix the message to append to the view description in the tree printout.
   *     Required if problemViews is supplied. Otherwise, null is acceptable.
   * @return a string for human consumption.
   */
  public static String getViewHierarchyErrorMessage(
      View rootView,
      final List<View> problemViews,
      final String errorHeader,
      final String problemViewSuffix) {
    return getViewHierarchyErrorMessage(
        rootView, problemViews, errorHeader, problemViewSuffix, Integer.MAX_VALUE);
  }

  /**
   * Prints out an error message featuring the view hierarchy starting at the rootView.
   *
   * @param rootView the root of the hierarchy tree to print out.
   * @param problemViews list of the views that you would like to point out are causing the error
   *     message or null, if you want to skip this feature.
   * @param errorHeader the header of the error message (should contain the description of why the
   *     error is happening).
   * @param problemViewSuffix the message to append to the view description in the tree printout.
   *     Required if problemViews is supplied. Otherwise, null is acceptable.
   * @param maxMsgLen the maximum message length. Use {@link Integer#MAX_VALUE} to avoid truncating
   *     the message.
   * @return a string for human consumption.
   */
  public static String getViewHierarchyErrorMessage(
      View rootView,
      final List<View> problemViews,
      final String errorHeader,
      final String problemViewSuffix,
      int maxMsgLen) {
    checkArgument(problemViews == null || problemViewSuffix != null);
    StringBuilder errorMessage = new StringBuilder(errorHeader);
    if (problemViewSuffix != null) {
      errorMessage.append(
          String.format(
              Locale.ROOT, "\nProblem views are marked with '%s' below.", problemViewSuffix));
    }

    String viewHierarchyDump =
        StringJoinerKt.joinToString(
            CollectionsKt.map(
                depthFirstViewTraversalWithDistance(rootView),
                viewAndDistance -> {
                  String formatString = "+%s%s ";
                  if (problemViews != null && problemViews.contains(viewAndDistance.getView())) {
                    formatString += problemViewSuffix;
                  }
                  return String.format(
                      Locale.ROOT,
                      formatString,
                      StringsKt.padStart(">", viewAndDistance.getDistanceFromRoot() + 1, '-'),
                      HumanReadables.describe(viewAndDistance.getView()));
                }),
            "\n|\n");

    errorMessage.append("\n\nView Hierarchy:\n").append(viewHierarchyDump);

    if (maxMsgLen < Integer.MAX_VALUE) {
      String suffix = " [truncated]";
      if (errorMessage.length() + suffix.length() > maxMsgLen) {
        errorMessage.delete(max(0, maxMsgLen - suffix.length()), errorMessage.length());
        errorMessage.append(suffix);
      }
    }

    return errorMessage.toString();
  }

  public static String describe(Cursor c) {
    if (c.isBeforeFirst()) {
      return "Cursor positioned before first element.";
    } else if (c.isAfterLast()) {
      return "Cursor positioned after last element.";
    }
    StringBuilder result = new StringBuilder("Row ").append(c.getPosition()).append(": {");
    String[] columns = c.getColumnNames();
    for (int i = 0; i < columns.length; i++) {
      result.append(columns[i]).append(":");
      int type = Cursor.FIELD_TYPE_STRING;
      if (Build.VERSION.SDK_INT > 10) {
        type = c.getType(i);
      }
      switch (type) {
        case Cursor.FIELD_TYPE_STRING:
          result.append("\"").append(c.getString(i)).append("\"");
          break;
        case Cursor.FIELD_TYPE_INTEGER:
          result.append(c.getLong(i));
          break;
        case Cursor.FIELD_TYPE_FLOAT:
          result.append(c.getDouble(i));
          result.append("f");
          break;
        case Cursor.FIELD_TYPE_NULL:
          result.append("null");
          break;
        case Cursor.FIELD_TYPE_BLOB:
          byte[] val = c.getBlob(i);
          result.append("[");
          for (int j = 0; j < 5 && j < val.length; j++) {
            result.append(val[j]);
            result.append(",");
          }
          if (5 < val.length) {
            result.append("... (").append(val.length - 5).append(" more elements)");
          }
          result.append("]");
          break;
        default:
          result.append("\"").append(c.getString(i)).append("\"");
          break;
      }
      result.append(", ");
    }
    result.append("}");
    return result.toString();
  }

  /**
   * Transforms an arbitrary view into a string with (hopefully) enough debug info.
   *
   * @param v nullable view
   * @return a string for human consumption.
   */
  public static String describe(View v) {
    if (null == v) {
      return "null";
    }
    StringBuilder builder = new StringBuilder(v.getClass().getSimpleName() + "{");
    builder.append("id=" + v.getId());
    if (v.getId() != View.NO_ID
        && v.getId() != 0
        && v.getResources() != null
        && !isViewIdGenerated(v.getId())) {
      try {
        builder.append(", res-name=" + v.getResources().getResourceEntryName(v.getId()));
      } catch (Resources.NotFoundException ignore) {
        // Do nothing.
      }
    }
    if (null != v.getContentDescription()) {
      builder.append(", desc=" + v.getContentDescription());
    }

    switch (v.getVisibility()) {
      case View.GONE:
        builder.append(", visibility=GONE");
        break;
      case View.INVISIBLE:
        builder.append(", visibility=INVISIBLE");
        break;
      case View.VISIBLE:
        builder.append(", visibility=VISIBLE");
        break;
      default:
        builder.append(", visibility=" + v.getVisibility());
    }

    builder
        .append(", width=" + v.getWidth())
        .append(", height=" + v.getHeight())
        .append(", has-focus=" + v.hasFocus())
        .append(", has-focusable=" + v.hasFocusable())
        .append(", has-window-focus=" + v.hasWindowFocus())
        .append(", is-clickable=" + v.isClickable())
        .append(", is-enabled=" + v.isEnabled())
        .append(", is-focused=" + v.isFocused())
        .append(", is-focusable=" + v.isFocusable())
        .append(", is-layout-requested=" + v.isLayoutRequested())
        .append(", is-selected=" + v.isSelected())
        .append(", layout-params=" + replaceHashCodes(v.getLayoutParams()))
        .append(", tag=" + v.getTag());

    if (null != v.getRootView()) {
      // pretty much only true in unit-tests.
      builder.append(", root-is-layout-requested=" + v.getRootView().isLayoutRequested());
    }

    EditorInfo ei = new EditorInfo();
    InputConnection ic = v.onCreateInputConnection(ei);
    boolean hasInputConnection = ic != null;
    builder.append(", has-input-connection=" + hasInputConnection);
    if (hasInputConnection) {
      StringBuilder sb = new StringBuilder();
      builder.append("[");
      Printer p = new StringBuilderPrinter(builder);
      ei.dump(p, "");
      builder.append("]");
      builder.append(", editor-info" + sb.toString().replace("\n", " "));
    }

    if (Build.VERSION.SDK_INT > 10) {
      builder.append(", x=" + v.getX()).append(", y=" + v.getY());
    }

    if (v instanceof TextView) {
      innerDescribe((TextView) v, builder);
    }
    if (v instanceof Checkable) {
      innerDescribe((Checkable) v, builder);
    }
    if (v instanceof ViewGroup) {
      innerDescribe((ViewGroup) v, builder);
    }
    builder.append("}");
    return builder.toString();
  }

  /** Replaces every hexadecimal hash code with "YYYYYY". */
  private static String replaceHashCodes(Object instance) {
    if (instance == null) {
      return null;
    }
    String str = instance.toString();
    Matcher matcher = OBJECT_HASH_CODE_PATTERN.matcher(str);
    if (matcher.find()) {
      str = str.substring(0, matcher.start(2)) + "YYYYYY" + str.substring(matcher.end(2));
    }
    return str;
  }

  private static void innerDescribe(TextView textBox, StringBuilder builder) {
    if (null != textBox.getText()) {
      builder.append(", text=" + textBox.getText());
    }

    if (null != textBox.getError()) {
      builder.append(", error-text=" + textBox.getError());
    }

    if (null != textBox.getHint()) {
      builder.append(", hint=" + textBox.getHint());
    }

    builder.append(", input-type=" + textBox.getInputType());
    builder.append(", ime-target=" + textBox.isInputMethodTarget());
    builder.append(", has-links=" + Boolean.toString(textBox.getUrls().length > 0));
  }

  private static void innerDescribe(Checkable checkable, StringBuilder builder) {
    builder.append(", is-checked=" + checkable.isChecked());
  }

  private static void innerDescribe(ViewGroup viewGroup, StringBuilder builder) {
    builder.append(", child-count=" + viewGroup.getChildCount());
  }
  /**
   * IDs generated by {@link View#generateViewId} will fail if used as a resource ID in attempted
   * resources lookups. This now logs an error in API 28, causing test failures. This method is
   * taken from {@link View#isViewIdGenerated} to prevent resource lookup to check if a view id was
   * generated.
   */
  private static boolean isViewIdGenerated(int id) {
    return (id & 0xFF000000) == 0 && (id & 0x00FFFFFF) != 0;
  }
}
