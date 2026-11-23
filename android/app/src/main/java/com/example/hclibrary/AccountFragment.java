package com.example.hclibrary;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AccountFragment extends Fragment {
    private static final String ARG_USERNAME = "username";
    private static final String ARG_ROLE = "role";

    private final List<Checkout> checkouts = new ArrayList<>();
    private String accountUsername = "";
    private String accountRole = "BORROWER";

    private static class Checkout {
        String barcode;
        String title;
        String author;
        String checkoutDate;
        String dueDate;

        Checkout(String barcode, String title, String author, String checkoutDate, String dueDate) {
            this.barcode = barcode;
            this.title = title;
            this.author = author;
            this.checkoutDate = checkoutDate;
            this.dueDate = dueDate;
        }
    }

    public static AccountFragment newInstance(String username, String role) {
        AccountFragment fragment = new AccountFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_ROLE, role != null ? role : "BORROWER");
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);
        TextView usernameView = view.findViewById(R.id.account_username);
        TextView roleLabel = view.findViewById(R.id.account_role_label);

        if (getArguments() != null) {
            accountUsername = getArguments().getString(ARG_USERNAME, "");
            accountRole = getArguments().getString(ARG_ROLE, "BORROWER");
        }

        usernameView.setText(accountUsername);
        boolean isEmployee = "EMPLOYEE".equalsIgnoreCase(accountRole);
        roleLabel.setText(isEmployee ? R.string.account_employee_label : R.string.account_member_label);
        roleLabel.setTextColor(ContextCompat.getColor(
                requireContext(),
                isEmployee ? R.color.cyan : R.color.available_green
        ));

        renderCheckouts(view);
        loadCheckoutsFromBackend(view);
        return view;
    }

    private void loadCheckoutsFromBackend(View root) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String encodedUser = URLEncoder.encode(accountUsername, "UTF-8");
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/checkouts/account/" + encodedUser);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    String body = readStream(conn.getInputStream());
                    JSONArray arr = new JSONArray(body);
                    List<Checkout> fetched = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        fetched.add(new Checkout(
                                obj.optString("barcode", ""),
                                obj.optString("title", ""),
                                obj.optString("author", ""),
                                obj.optString("checkoutDate", ""),
                                obj.optString("dueDate", "")
                        ));
                    }
                    Activity activity = getActivity();
                    if (activity != null && isAdded()) {
                        activity.runOnUiThread(() -> {
                            if (isAdded()) {
                                checkouts.clear();
                                checkouts.addAll(fetched);
                                renderCheckouts(root);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Keep empty list when server is unreachable.
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void returnBook(Checkout checkout, View root) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/checkouts/checkin");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("employeeUsername", accountUsername);
                payload.put("barcode", checkout.barcode);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                boolean success = code >= 200 && code < 300;
                String body = readStream(success ? conn.getInputStream() : conn.getErrorStream());
                JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String msg = json.optString(success ? "message" : "error",
                        success ? "Book returned" : "Unable to return book");

                Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                            if (success) {
                                checkouts.remove(checkout);
                                renderCheckouts(root);
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).refreshCatalog();
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Activity activity = getActivity();
                if (activity != null && isAdded()) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(getContext(), "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void renderCheckouts(View root) {
        LinearLayout list = root.findViewById(R.id.checkout_list);
        TextView emptyState = root.findViewById(R.id.empty_checkouts);
        list.removeAllViews();
        emptyState.setVisibility(checkouts.isEmpty() ? View.VISIBLE : View.GONE);

        boolean isEmployee = "EMPLOYEE".equalsIgnoreCase(accountRole);

        for (Checkout checkout : checkouts) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 18, 20, 18);
            card.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_card));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 14);
            card.setLayoutParams(cardParams);

            TextView title = new TextView(requireContext());
            title.setText(checkout.title);
            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.card_title));
            title.setTextSize(18);
            title.setTypeface(null, Typeface.BOLD);

            TextView author = new TextView(requireContext());
            author.setText(checkout.author);
            author.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted_text));
            author.setTextSize(14);
            author.setPadding(0, 6, 0, 14);

            LinearLayout dates = new LinearLayout(requireContext());
            dates.setOrientation(LinearLayout.VERTICAL);
            TextView checkoutDate = dateLabel("Checked out: " + checkout.checkoutDate);
            TextView dueDate = dateLabel("Due date: " + checkout.dueDate);
            dates.addView(checkoutDate);
            dates.addView(dueDate);

            card.addView(title);
            card.addView(author);
            card.addView(dates);

            if (isEmployee) {
                Button returnButton = new Button(requireContext());
                returnButton.setText(R.string.return_book);
                returnButton.setTextColor(Color.WHITE);
                returnButton.setAllCaps(false);
                returnButton.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.cyan)));
                returnButton.setOnClickListener(v -> returnBook(checkout, root));
                card.addView(returnButton);
            }

            list.addView(card);
        }
    }

    private TextView dateLabel(String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextColor(ContextCompat.getColor(requireContext(), R.color.muted_text));
        label.setTextSize(13);
        label.setGravity(Gravity.START);
        return label;
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
