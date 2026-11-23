package com.example.hclibrary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CreateAccountFragment extends Fragment {

    public CreateAccountFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_account, container, false);

        EditText usernameField = view.findViewById(R.id.usernameField);
        EditText emailField = view.findViewById(R.id.emailField);
        EditText passwordField = view.findViewById(R.id.editTextTextPassword);
        Button submitButton = view.findViewById(R.id.submitCreateAccountButton);
        Button backToLoginButton = view.findViewById(R.id.backToLoginButton);

        submitButton.setOnClickListener(v -> {
            String user = usernameField.getText().toString().trim();
            String email = emailField.getText().toString().trim();
            String pass = passwordField.getText().toString();
            sendCreateAccountRequest(user, email, pass);
        });

        backToLoginButton.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit()
        );

        return view;
    }

    private void sendCreateAccountRequest(String user, String email, String pass) {
        if (user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(getContext(), "Please enter username, email, and password", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/accounts/register");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("username", user);
                jsonBody.put("email", email);
                jsonBody.put("password", pass);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                InputStream stream = (responseCode >= 200 && responseCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String responseText = "";
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        responseText = sb.toString();
                    }
                }

                JSONObject resJson = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
                boolean isSuccess = responseCode >= 200 && responseCode < 300;
                String message = resJson.optString(
                        isSuccess ? "message" : "error",
                        isSuccess ? "Account created successfully!" : "Error creating account (Code " + responseCode + ")"
                );

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                        if (isSuccess) {
                            getParentFragmentManager().beginTransaction()
                                    .replace(R.id.fragment_container, new LoginFragment())
                                    .commit();
                        }
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Connection error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
}
