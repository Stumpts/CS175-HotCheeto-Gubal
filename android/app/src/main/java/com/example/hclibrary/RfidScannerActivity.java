package com.example.hclibrary;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class RfidScannerActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    public static final String EXTRA_SEARCH_QUERY = "extra_search_query";

    @Nullable
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rfid_scanner);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rfid_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        LinearLayout statusCard = findViewById(R.id.scanner_status_card);
        statusCard.setBackground(createCardDrawable());

        LinearLayout scannedBookCard = findViewById(R.id.scanned_book_card);
        scannedBookCard.setBackground(createCardDrawable());

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // Initialize hardware NFC / HF-RFID adapter if available on this phone.
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        updateReaderStatusBanner();

        EditText manualInput = findViewById(R.id.manual_tag_input);
        Button manualReadButton = findViewById(R.id.manual_read_button);
        manualReadButton.setOnClickListener(v -> {
            String rawTag = manualInput.getText() != null
                    ? manualInput.getText().toString().trim()
                    : "";
            if (!rawTag.isEmpty()) {
                String upperTag = rawTag.toUpperCase(Locale.ROOT);
                handleScannedTag(upperTag, upperTag);
            } else {
                Toast.makeText(this, "Enter an RFID tag ID first", Toast.LENGTH_SHORT).show();
            }
        });

        renderSampleTagButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReaderStatusBanner();
        // Enable reader mode for ISO 15693 (NFC-V library tags) and standard NFC-A/B/F tags.
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            int flags = NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F;
            nfcAdapter.enableReaderMode(this, this, flags, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (tag == null) {
            return;
        }
        String uidHex = formatUidHex(tag.getId());
        String ndefPayload = readNdefText(tag);
        runOnUiThread(() -> {
            String lookupKey = ndefPayload != null ? ndefPayload : uidHex;
            handleScannedTag(lookupKey, uidHex);
        });
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

    private void updateReaderStatusBanner() {
        TextView statusTitle = findViewById(R.id.rfid_status_title);
        TextView statusSubtitle = findViewById(R.id.rfid_status_subtitle);
        if (nfcAdapter == null) {
            statusTitle.setText(R.string.rfid_no_hardware_title);
            statusSubtitle.setText(R.string.rfid_no_hardware_subtitle);
        } else if (!nfcAdapter.isEnabled()) {
            statusTitle.setText(R.string.rfid_disabled_title);
            statusSubtitle.setText(R.string.rfid_disabled_subtitle);
        } else {
            statusTitle.setText(R.string.rfid_ready_title);
            statusSubtitle.setText(R.string.rfid_ready_subtitle);
        }
    }

    private void handleScannedTag(String tagKey, String rawUid) {
        MainActivity.Book matchedBook = null;
        for (MainActivity.Book book : MainActivity.Companion.getBooks()) {
            if (book.getRfidTag().equalsIgnoreCase(tagKey)
                    || book.getRfidTag().equalsIgnoreCase(rawUid)
                    || book.getTitle().equalsIgnoreCase(tagKey)) {
                matchedBook = book;
                break;
            }
        }

        LinearLayout card = findViewById(R.id.scanned_book_card);
        TextView tagIdView = findViewById(R.id.scanned_tag_id);
        TextView titleView = findViewById(R.id.scanned_book_title);
        TextView authorView = findViewById(R.id.scanned_book_author);
        TextView statusView = findViewById(R.id.scanned_book_status);
        Button toggleButton = findViewById(R.id.action_toggle_status_button);
        Button searchButton = findViewById(R.id.action_search_book_button);

        card.setVisibility(View.VISIBLE);
        tagIdView.setText("RFID TAG: " + rawUid);

        if (matchedBook != null) {
            final MainActivity.Book targetBook = matchedBook;
            titleView.setText(targetBook.getTitle());
            authorView.setText(targetBook.getAuthor());
            updateBookActionViews(targetBook, statusView, toggleButton);

            toggleButton.setVisibility(View.VISIBLE);
            toggleButton.setOnClickListener(v -> {
                targetBook.setAvailable(!targetBook.getAvailable());
                String actionMsg = targetBook.getAvailable()
                        ? "Checked in \"" + targetBook.getTitle() + "\""
                        : "Checked out \"" + targetBook.getTitle() + "\"";
                Toast.makeText(this, actionMsg, Toast.LENGTH_SHORT).show();
                updateBookActionViews(targetBook, statusView, toggleButton);
                renderSampleTagButtons();
            });

            searchButton.setVisibility(View.VISIBLE);
            searchButton.setOnClickListener(v -> {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SEARCH_QUERY, targetBook.getTitle());
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        } else {
            titleView.setText("Unrecognized RFID Tag");
            authorView.setText("No catalog item is linked to tag " + rawUid + ".");
            statusView.setText("● Not in catalog");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.checked_out_orange));
            toggleButton.setVisibility(View.GONE);
            searchButton.setVisibility(View.GONE);
        }
    }

    private void updateBookActionViews(
            MainActivity.Book book,
            TextView statusView,
            Button toggleButton
    ) {
        statusView.setText(book.getAvailable() ? "● Available" : "● Checked out");
        statusView.setTextColor(ContextCompat.getColor(
                this,
                book.getAvailable() ? R.color.available_green : R.color.checked_out_orange
        ));
        toggleButton.setText(book.getAvailable() ? "Check Out" : "Check In");
    }

    private void renderSampleTagButtons() {
        LinearLayout container = findViewById(R.id.sample_tags_container);
        container.removeAllViews();
        for (MainActivity.Book book : MainActivity.Companion.getBooks()) {
            LinearLayout row = new LinearLayout(this);
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

            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView tagLabel = new TextView(this);
            tagLabel.setText("Tag: " + book.getRfidTag());
            tagLabel.setTextSize(12f);
            tagLabel.setTextColor(ContextCompat.getColor(this, R.color.blue));
            tagLabel.setTypeface(null, Typeface.BOLD);

            TextView bookLabel = new TextView(this);
            String availabilityText = book.getAvailable() ? "Available" : "Checked out";
            bookLabel.setText(book.getTitle() + " (" + availabilityText + ")");
            bookLabel.setTextSize(14f);
            bookLabel.setTextColor(ContextCompat.getColor(this, R.color.card_title));

            infoLayout.addView(tagLabel);
            infoLayout.addView(bookLabel);

            Button scanBtn = new Button(this);
            scanBtn.setText("Simulate Scan");
            scanBtn.setTextSize(12f);
            scanBtn.setOnClickListener(v -> handleScannedTag(book.getRfidTag(), book.getRfidTag()));

            row.addView(infoLayout);
            row.addView(scanBtn);
            container.addView(row);
        }
    }

    private GradientDrawable createCardDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ContextCompat.getColor(this, R.color.card_background));
        drawable.setCornerRadius(20f);
        drawable.setStroke(1, ContextCompat.getColor(this, R.color.card_border));
        return drawable;
    }
}
