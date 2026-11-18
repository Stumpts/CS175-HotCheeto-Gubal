package com.example.hclibrary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import androidx.fragment.app.Fragment;

public class AccountFragment extends Fragment {
    private static final String ARG_USERNAME = "username";
    private final java.util.List<Checkout> checkouts = new java.util.ArrayList<>();

    private static class Checkout {
        String title;
        String author;
        String checkoutDate;
        String dueDate;

        Checkout(String title, String author, String checkoutDate, String dueDate) {
            this.title = title;
            this.author = author;
            this.checkoutDate = checkoutDate;
            this.dueDate = dueDate;
        }
    }

    public static AccountFragment newInstance(String username) {
        AccountFragment fragment = new AccountFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        fragment.setArguments(args);
        return fragment;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);
        TextView username = view.findViewById(R.id.account_username);
        String name = getArguments() == null ? "" : getArguments().getString(ARG_USERNAME, "");
        username.setText(name);
        loadSampleCheckouts();
        renderCheckouts(view);
        return view;
    }

    // Temporary data for the frontend until checkout data comes from the backend.
    private void loadSampleCheckouts() {
        checkouts.clear();
        checkouts.add(new Checkout("Sample Book One", "Sample Author", "Nov 10, 2026", "Nov 24, 2026"));
        checkouts.add(new Checkout("Sample Book Two", "Library Author", "Nov 12, 2026", "Nov 26, 2026"));
    }

    private void renderCheckouts(View root) {
        LinearLayout list = root.findViewById(R.id.checkout_list);
        TextView emptyState = root.findViewById(R.id.empty_checkouts);
        list.removeAllViews();
        emptyState.setVisibility(checkouts.isEmpty() ? View.VISIBLE : View.GONE);

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

            Button returnButton = new Button(requireContext());
            returnButton.setText(R.string.return_book);
            returnButton.setTextColor(Color.WHITE);
            returnButton.setAllCaps(false);
            returnButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.cyan)));
            returnButton.setOnClickListener(v -> {
                checkouts.remove(checkout);
                renderCheckouts(root);
            });

            card.addView(title);
            card.addView(author);
            card.addView(dates);
            card.addView(returnButton);
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
}
