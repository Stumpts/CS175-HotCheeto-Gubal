package com.example.hclibrary;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Locale;

public class EmployeeCirculationFragment extends Fragment implements NfcAdapter.ReaderCallback {

    private static final String ARG_EMPLOYEE_USERNAME = "employee_username";
    private static final String ARG_INITIAL_BARCODE = "initial_barcode";

    private static class CopyRecord {
        String barcode;
        String title;
        String author;
        String isbn;
        boolean available;
        @Nullable
        String borrowerUsername;
        @Nullable
        String dueDate;
    }

    private String employeeUsername = "";
    @Nullable
    private String initialBarcode;
    private boolean isCheckoutMode = true;
    @Nullable
    private CopyRecord currentCopy;
    private final List<CopyRecord> copies = new ArrayList<>();

    @Nullable
    private NfcAdapter nfcAdapter;

    private Button modeCheckoutButton;
    private Button modeCheckinButton;
    private LinearLayout borrowerInputContainer;
    private EditText borrowerUsernameInput;
    private EditText manualBarcodeInput;
    private TextView rfidStatusTitle;
    private TextView rfidStatusSubtitle;
    private LinearLayout scannedCopyCard;
    private TextView scannedBarcodeId;
    private TextView scannedCopyTitle;
    private TextView scannedCopyAuthor;
    private TextView scannedCopyStatus;
    private TextView scannedCopyLoanInfo;
    private Button actionCirculationButton;
    private LinearLayout copiesContainer;

    public EmployeeCirculationFragment() {
        // Required empty public constructor
    }

    public static EmployeeCirculationFragment newInstance(String employeeUsername, @Nullable String initialBarcode) {
        EmployeeCirculationFragment fragment = new EmployeeCirculationFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EMPLOYEE_USERNAME, employeeUsername);
        if (initialBarcode != null && !initialBarcode.isEmpty()) {
            args.putString(ARG_INITIAL_BARCODE, initialBarcode);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            employeeUsername = getArguments().getString(ARG_EMPLOYEE_USERNAME, "");
            initialBarcode = getArguments().getString(ARG_INITIAL_BARCODE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_employee_circulation, container, false);

        modeCheckoutButton = view.findViewById(R.id.mode_checkout_button);
        modeCheckinButton = view.findViewById(R.id.mode_checkin_button);
        borrowerInputContainer = view.findViewById(R.id.borrower_input_container);
        borrowerUsernameInput = view.findViewById(R.id.borrower_username_input);
        manualBarcodeInput = view.findViewById(R.id.manual_barcode_input);
        rfidStatusTitle = view.findViewById(R.id.employee_rfid_status_title);
        rfidStatusSubtitle = view.findViewById(R.id.employee_rfid_status_subtitle);
        scannedCopyCard = view.findViewById(R.id.scanned_copy_card);
        scannedBarcodeId = view.findViewById(R.id.scanned_barcode_id);
        scannedCopyTitle = view.findViewById(R.id.scanned_copy_title);
        scannedCopyAuthor = view.findViewById(R.id.scanned_copy_author);
        scannedCopyStatus = view.findViewById(R.id.scanned_copy_status);
        scannedCopyLoanInfo = view.findViewById(R.id.scanned_copy_loan_info);
        actionCirculationButton = view.findViewById(R.id.action_circulation_button);
        copiesContainer = view.findViewById(R.id.copies_container);

        Button manualLookupButton = view.findViewById(R.id.manual_lookup_button);
        Button clearButton = view.findViewById(R.id.action_clear_button);

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext());
        updateReaderStatusBanner();
        updateModeUi();

        modeCheckoutButton.setOnClickListener(v -> {
            isCheckoutMode = true;
            updateModeUi();
            if (currentCopy != null) {
                renderSelectedCopy(currentCopy);
            }
        });

        modeCheckinButton.setOnClickListener(v -> {
            isCheckoutMode = false;
            updateModeUi();
            if (currentCopy != null) {
                renderSelectedCopy(currentCopy);
            }
        });

        manualLookupButton.setOnClickListener(v -> submitManualBarcode());

        manualBarcodeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitManualBarcode();
                return true;
            }
            return false;
        });

        clearButton.setOnClickListener(v -> {
            currentCopy = null;
            manualBarcodeInput.setText("");
            scannedCopyCard.setVisibility(View.GONE);
        });

        fetchCopiesFromBackend();

        if (initialBarcode != null && !initialBarcode.isEmpty()) {
            manualBarcodeInput.setText(initialBarcode);
            lookupBarcode(initialBarcode);
            initialBarcode = null;
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateReaderStatusBanner();
        Activity activity = getActivity();
        if (nfcAdapter != null && nfcAdapter.isEnabled() && activity != null) {
            int flags = NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F;
            nfcAdapter.enableReaderMode(activity, this, flags, null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Activity activity = getActivity();
        if (nfcAdapter != null && activity != null) {
            nfcAdapter.disableReaderMode(activity);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (tag == null) {
            return;
        }
        String uidHex = formatUidHex(tag.getId());
        String ndefPayload = readNdefText(tag);
        String scannedCode = (ndefPayload != null && !ndefPayload.isEmpty()) ? ndefPayload : uidHex;
        Activity activity = getActivity();
        if (activity != null && isAdded()) {
            activity.runOnUiThread(() -> {
                manualBarcodeInput.setText(scannedCode);
                manualBarcodeInput.setSelection(scannedCode.length());
                lookupBarcode(scannedCode);
            });
        }
    }

    private void submitManualBarcode() {
        String raw = manualBarcodeInput.getText() != null
                ? manualBarcodeInput.getText().toString().trim()
                : "";
        if (raw.isEmpty()) {
            Toast.makeText(getContext(), "Enter a barcode or scan an RFID tag first", Toast.LENGTH_SHORT).show();
            return;
        }
        lookupBarcode(raw);
    }

    private void updateModeUi() {
        if (!isAdded()) {
            return;
        }
        int activeCheckoutColor = ContextCompat.getColor(requireContext(), R.color.green);
        int activeCheckinColor = ContextCompat.getColor(requireContext(), R.color.cyan);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.grey_green);

        modeCheckoutButton.setBackgroundTintList(
                ColorStateList.valueOf(isCheckoutMode ? activeCheckoutColor : inactiveColor));
        modeCheckinButton.setBackgroundTintList(
                ColorStateList.valueOf(isCheckoutMode ? inactiveColor : activeCheckinColor));
        borrowerInputContainer.setVisibility(isCheckoutMode ? View.VISIBLE : View.GONE);
    }

    private void updateReaderStatusBanner() {
        if (rfidStatusTitle == null || rfidStatusSubtitle == null) {
            return;
        }
        if (nfcAdapter == null) {
            rfidStatusTitle.setText(R.string.rfid_no_hardware_title);
            rfidStatusSubtitle.setText(R.string.rfid_no_hardware_subtitle);
        } else if (!nfcAdapter.isEnabled()) {
            rfidStatusTitle.setText(R.string.rfid_disabled_title);
            rfidStatusSubtitle.setText(R.string.rfid_disabled_subtitle);
        } else {
            rfidStatusTitle.setText(R.string.rfid_ready_title);
            rfidStatusSubtitle.setText(R.string.rfid_ready_subtitle);
        }
    }

    private void fetchCopiesFromBackend() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/checkouts/copies");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    String body = readStream(conn.getInputStream());
                    JSONArray array = new JSONArray(body);
                    List<CopyRecord> fetched = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        fetched.add(parseCopyRecord(array.getJSONObject(i)));
                    }
                    runOnUiIfAdded(() -> {
                        copies.clear();
                        copies.addAll(fetched);
                        renderCopiesList();
                    });
                } else {
                    runOnUiIfAdded(this::renderFallbackCopies);
                }
            } catch (Exception ignored) {
                runOnUiIfAdded(this::renderFallbackCopies);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void lookupBarcode(String barcode) {
        String trimmed = barcode.trim();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String encoded = URLEncoder.encode(trimmed, "UTF-8");
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/checkouts/lookup?barcode=" + encoded);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    String body = readStream(conn.getInputStream());
                    CopyRecord record = parseCopyRecord(new JSONObject(body));
                    runOnUiIfAdded(() -> {
                        currentCopy = record;
                        renderSelectedCopy(record);
                    });
                } else {
                    String errBody = readStream(conn.getErrorStream());
                    String errMsg = extractErrorMessage(errBody, "No catalog item is linked to barcode " + trimmed + ".");
                    runOnUiIfAdded(() -> renderNotFoundCopy(trimmed, errMsg));
                }
            } catch (Exception e) {
                runOnUiIfAdded(() -> {
                    CopyRecord local = findLocalCopy(trimmed);
                    if (local != null) {
                        currentCopy = local;
                        renderSelectedCopy(local);
                    } else {
                        renderNotFoundCopy(trimmed, "Could not reach server to verify barcode " + trimmed + ".");
                    }
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void performCirculationAction(CopyRecord targetCopy, boolean checkout) {
        actionCirculationButton.setEnabled(false);
        String borrower = borrowerUsernameInput.getText() != null
                ? borrowerUsernameInput.getText().toString().trim()
                : "";

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = checkout ? "/api/checkouts/checkout" : "/api/checkouts/checkin";
                URL url = new URL(ApiConfig.getBaseUrl() + endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("employeeUsername", employeeUsername);
                payload.put("barcode", targetCopy.barcode);
                if (checkout && !borrower.isEmpty()) {
                    payload.put("borrowerUsername", borrower);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                boolean success = code >= 200 && code < 300;
                String body = readStream(success ? conn.getInputStream() : conn.getErrorStream());
                JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);

                if (success) {
                    String msg = json.optString("message", checkout ? "Book checked out" : "Book checked in");
                    CopyRecord updated = json.has("copy")
                            ? parseCopyRecord(json.getJSONObject("copy"))
                            : targetCopy;
                    runOnUiIfAdded(() -> {
                        actionCirculationButton.setEnabled(true);
                        currentCopy = updated;
                        renderSelectedCopy(updated);
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        fetchCopiesFromBackend();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).refreshCatalog();
                        }
                    });
                } else {
                    String err = json.optString("error", "Request failed (HTTP " + code + ")");
                    runOnUiIfAdded(() -> {
                        actionCirculationButton.setEnabled(true);
                        Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiIfAdded(() -> {
                    actionCirculationButton.setEnabled(true);
                    Toast.makeText(getContext(), "Connection error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void renderSelectedCopy(CopyRecord record) {
        if (!isAdded()) {
            return;
        }
        scannedCopyCard.setVisibility(View.VISIBLE);
        scannedBarcodeId.setText("BARCODE / RFID: " + record.barcode);
        scannedCopyTitle.setText(record.title);
        String authorLine = record.isbn != null && !record.isbn.isEmpty()
                ? record.author + " • ISBN: " + record.isbn
                : record.author;
        scannedCopyAuthor.setText(authorLine);

        if (record.available) {
            scannedCopyStatus.setText("● Available");
            scannedCopyStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.available_green));
            if (isCheckoutMode) {
                scannedCopyLoanInfo.setVisibility(View.GONE);
                actionCirculationButton.setVisibility(View.VISIBLE);
                actionCirculationButton.setEnabled(true);
                actionCirculationButton.setText(R.string.employee_mode_checkout);
                actionCirculationButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)));
                actionCirculationButton.setOnClickListener(v -> performCirculationAction(record, true));
            } else {
                scannedCopyLoanInfo.setVisibility(View.VISIBLE);
                scannedCopyLoanInfo.setText("This copy is already checked in.");
                actionCirculationButton.setVisibility(View.VISIBLE);
                actionCirculationButton.setEnabled(true);
                actionCirculationButton.setText("Switch to Check Out");
                actionCirculationButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)));
                actionCirculationButton.setOnClickListener(v -> {
                    isCheckoutMode = true;
                    updateModeUi();
                    renderSelectedCopy(record);
                });
            }
        } else {
            scannedCopyStatus.setText("● Checked out");
            scannedCopyStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.checked_out_orange));
            StringBuilder loanDetails = new StringBuilder();
            if (record.borrowerUsername != null && !record.borrowerUsername.isEmpty()) {
                loanDetails.append("Borrower: ").append(record.borrowerUsername);
            }
            if (record.dueDate != null && !record.dueDate.isEmpty()) {
                if (loanDetails.length() > 0) {
                    loanDetails.append(" • ");
                }
                loanDetails.append("Due: ").append(record.dueDate);
            }
            if (loanDetails.length() > 0) {
                scannedCopyLoanInfo.setVisibility(View.VISIBLE);
                scannedCopyLoanInfo.setText(loanDetails.toString());
            } else {
                scannedCopyLoanInfo.setVisibility(View.GONE);
            }

            actionCirculationButton.setVisibility(View.VISIBLE);
            actionCirculationButton.setEnabled(true);
            actionCirculationButton.setText(R.string.employee_mode_checkin);
            actionCirculationButton.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.cyan)));
            actionCirculationButton.setOnClickListener(v -> performCirculationAction(record, false));
        }
    }

    private void renderNotFoundCopy(String barcode, String message) {
        if (!isAdded()) {
            return;
        }
        currentCopy = null;
        scannedCopyCard.setVisibility(View.VISIBLE);
        scannedBarcodeId.setText("BARCODE / RFID: " + barcode);
        scannedCopyTitle.setText("Unrecognized Barcode / RFID");
        scannedCopyAuthor.setText(message);
        scannedCopyStatus.setText("● Not in catalog");
        scannedCopyStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.checked_out_orange));
        scannedCopyLoanInfo.setVisibility(View.GONE);
        actionCirculationButton.setVisibility(View.GONE);
    }

    private void renderFallbackCopies() {
        copies.clear();
        for (MainActivity.Book book : MainActivity.Companion.getBooks()) {
            CopyRecord rec = new CopyRecord();
            rec.barcode = book.getRfidTag();
            rec.title = book.getTitle();
            rec.author = book.getAuthor();
            rec.isbn = "";
            rec.available = book.getAvailable();
            copies.add(rec);
        }
        renderCopiesList();
    }

    private void renderCopiesList() {
        if (!isAdded() || copiesContainer == null) {
            return;
        }
        copiesContainer.removeAllViews();
        for (CopyRecord copy : copies) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 14, 16, 14);
            row.setBackground(createCardDrawable());
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, 10);
            row.setLayoutParams(rowParams);

            LinearLayout infoLayout = new LinearLayout(requireContext());
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView barcodeLabel = new TextView(requireContext());
            barcodeLabel.setText("Barcode / Tag: " + copy.barcode);
            barcodeLabel.setTextSize(12f);
            barcodeLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue));
            barcodeLabel.setTypeface(null, Typeface.BOLD);

            TextView bookLabel = new TextView(requireContext());
            String statusText;
            if (copy.available) {
                statusText = "Available";
            } else if (copy.borrowerUsername != null && !copy.borrowerUsername.isEmpty()) {
                statusText = "Checked out • " + copy.borrowerUsername;
            } else {
                statusText = "Checked out";
            }
            bookLabel.setText(copy.title + " (" + statusText + ")");
            bookLabel.setTextSize(14f);
            bookLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.card_title));

            infoLayout.addView(barcodeLabel);
            infoLayout.addView(bookLabel);

            Button scanBtn = new Button(requireContext());
            scanBtn.setText("Simulate Scan");
            scanBtn.setTextSize(12f);
            scanBtn.setAllCaps(false);
            scanBtn.setOnClickListener(v -> {
                manualBarcodeInput.setText(copy.barcode);
                manualBarcodeInput.setSelection(copy.barcode.length());
                lookupBarcode(copy.barcode);
            });

            row.addView(infoLayout);
            row.addView(scanBtn);
            copiesContainer.addView(row);
        }
    }

    @Nullable
    private CopyRecord findLocalCopy(String barcode) {
        for (CopyRecord copy : copies) {
            if (copy.barcode.equalsIgnoreCase(barcode)) {
                return copy;
            }
        }
        return null;
    }

    private CopyRecord parseCopyRecord(JSONObject obj) {
        CopyRecord rec = new CopyRecord();
        rec.barcode = obj.optString("barcode", "");
        rec.title = obj.optString("title", "");
        rec.author = obj.optString("author", "");
        rec.isbn = obj.optString("isbn", "");
        rec.available = obj.optBoolean("available", true);
        rec.borrowerUsername = obj.isNull("borrowerUsername") ? null : obj.optString("borrowerUsername", null);
        rec.dueDate = obj.isNull("dueDate") ? null : obj.optString("dueDate", null);
        return rec;
    }

    private String formatUidHex(byte[] idBytes) {
        if (idBytes == null || idBytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idBytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%02X", idBytes[i]));
        }
        return sb.toString();
    }

    @Nullable
    private String readNdefText(Tag tag) {
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                return null;
            }
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            ndef.close();
            if (message == null) {
                return null;
            }
            NdefRecord[] records = message.getRecords();
            if (records == null || records.length == 0) {
                return null;
            }
            byte[] payload = records[0].getPayload();
            if (payload == null || payload.length == 0) {
                return null;
            }
            int languageCodeLength = payload[0] & 0x3F;
            if (payload.length <= languageCodeLength + 1) {
                return null;
            }
            return new String(
                    payload,
                    languageCodeLength + 1,
                    payload.length - languageCodeLength - 1,
                    StandardCharsets.UTF_8
            ).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStream(@Nullable InputStream stream) throws Exception {
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

    private String extractErrorMessage(String body, String fallback) {
        try {
            if (body != null && !body.isEmpty()) {
                JSONObject json = new JSONObject(body);
                String err = json.optString("error", "");
                if (!err.isEmpty()) {
                    return err;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void runOnUiIfAdded(Runnable action) {
        Activity activity = getActivity();
        if (activity != null && isAdded()) {
            activity.runOnUiThread(() -> {
                if (isAdded()) {
                    action.run();
                }
            });
        }
    }

    private GradientDrawable createCardDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ContextCompat.getColor(requireContext(), R.color.card_background));
        drawable.setCornerRadius(20f);
        drawable.setStroke(1, ContextCompat.getColor(requireContext(), R.color.card_border));
        return drawable;
    }
}
