package de.slg.messenger;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import de.slg.leoapp.R;
import de.slg.leoapp.Utils;

public class ChatActivity extends AppCompatActivity {
    public static Chat chat;
    public static String chatname;
    private Message[] messagesArray;

    private Menu menu;
    private EditText etEditChatName;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private String message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.registerChatActivity(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messagesArray = new Message[0];
        Utils.receive();

        initToolbar();
        initSendMessage();
        initRecyclerView();

        Utils.getMessengerDBConnection().setMessagesRead(chat);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.messenger_chat, menu);
        this.menu = menu;
        if (chat.chatTyp == Chat.Chattype.PRIVATE)
            menu.clear();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        if (mi.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (mi.getItemId() == R.id.action_edtiParticipants) {
            startEditChat();
        } else if (mi.getItemId() == R.id.action_editChat) {
            setChatNameEditable(true);
        } else if (mi.getItemId() == R.id.action_cancel) {
            setChatNameEditable(false);
        } else if (mi.getItemId() == R.id.action_confirm) {
            confirmEdit();
            setChatNameEditable(false);
        }
        return true;
    }

    private void initRecyclerView() {
        messagesArray = Utils.getMessengerDBConnection().getMessagesFromChat(chat);

        rvMessages = (RecyclerView) findViewById(R.id.recyclerViewMessages);
        rvMessages.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        rvMessages.setAdapter(new MessageAdapter());
    }

    private void initToolbar() {
        Toolbar actionBar = (Toolbar) findViewById(R.id.actionBarChat);
        actionBar.setTitleTextColor(getResources().getColor(android.R.color.white));
        actionBar.setTitle(chatname);
        setSupportActionBar(actionBar);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back_white_24dp);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        etEditChatName = (EditText) findViewById(R.id.editTextEditChatName);
        etEditChatName.setVisibility(View.GONE);
    }

    private void initSendMessage() {
        etMessage = (EditText) findViewById(R.id.inputMessage);

        FloatingActionButton sendButton = (FloatingActionButton) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
    }

    private String getMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                message = etMessage.getText().toString();
            }
        });
        while (message.length() > 0 && message.charAt(0) == ' ')
            message = message.substring(1);
        while (message.length() > 0 && message.charAt(message.length() - 1) == ' ')
            message = message.substring(0, message.length() - 1);
        return message;
    }

    private void sendMessage() {
        String message = getMessage();
        if (Utils.checkNetwork()) {
            if (message.length() > 0 && chat != null) {
                new SendMessage().execute(message);
                etMessage.setText("");
                Utils.receive();
                refreshUI();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Verbinde dich mit dem Internet um Nchrichten zu senden.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startEditChat() {
        ChatEditActivity.currentChat = chat;
        startActivity(new Intent(getApplicationContext(), ChatEditActivity.class));
    }

    private void setChatNameEditable(boolean b) {
        if (b) {
            menu.clear();
            getSupportActionBar().setTitle("");
            etEditChatName.setText(chat.chatName);
            etEditChatName.setVisibility(View.VISIBLE);
            getMenuInflater().inflate(R.menu.messenger_confirm_action, menu);
        } else {
            menu.clear();
            getSupportActionBar().setTitle(chat.chatName);
            etEditChatName.setVisibility(View.GONE);
            getMenuInflater().inflate(R.menu.messenger_chat, menu);
        }
    }

    private void confirmEdit() {
        chat.chatName = etEditChatName.getText().toString();
        new SendChatname().execute();
    }

    public void refreshUI() {
        messagesArray = Utils.getMessengerDBConnection().getMessagesFromChat(chat);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rvMessages.swapAdapter(new MessageAdapter(), false);
            }
        });
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

    private class MessageAdapter extends RecyclerView.Adapter<ViewHolder> {

        private Chat.Chattype chattype;
        private LayoutInflater inflater;
        private TextView nachricht, absender, uhrzeit, datum;

        MessageAdapter() {
            super();
            this.inflater = getLayoutInflater();
            this.chattype = chat.chatTyp;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(inflater.inflate(R.layout.list_item_message, null));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Message current = messagesArray[position];
            View v = holder.itemView;
            boolean first = position == 0 || !gleicherTag(current.sendDate, messagesArray[position - 1].sendDate);
            boolean mine = current.senderId == Utils.getUserID();
            if (mine) {
                LinearLayout l1 = (LinearLayout) v.findViewById(R.id.wrapperlayout1);
                LinearLayout l2 = (LinearLayout) v.findViewById(R.id.wrapperlayout2);
                LinearLayout l3 = (LinearLayout) v.findViewById(R.id.wrapperlayout3);
                l1.setGravity(Gravity.END);
                l2.setGravity(Gravity.END);
                l3.setGravity(Gravity.END);
                l3.setEnabled(true);
                v.findViewById(R.id.absender).setVisibility(View.GONE);
            } else {
                LinearLayout l1 = (LinearLayout) v.findViewById(R.id.wrapperlayout1);
                LinearLayout l2 = (LinearLayout) v.findViewById(R.id.wrapperlayout2);
                LinearLayout l3 = (LinearLayout) v.findViewById(R.id.wrapperlayout3);
                l1.setGravity(Gravity.START);
                l2.setGravity(Gravity.START);
                l3.setGravity(Gravity.START);
                l3.setEnabled(false);
                absender = (TextView) v.findViewById(R.id.absender);
                absender.setText(current.senderName);
                if (chattype == Chat.Chattype.PRIVATE) {
                    v.findViewById(R.id.absender).setVisibility(View.GONE);
                } else {
                    v.findViewById(R.id.absender).setVisibility(View.VISIBLE);
                }
            }
            nachricht = (TextView) v.findViewById(R.id.nachricht);
            nachricht.setText(current.messageText);
            uhrzeit = (TextView) v.findViewById(R.id.datum);
            uhrzeit.setVisibility(View.VISIBLE);
            uhrzeit.setText(current.getTime());
            if (first) {
                datum = (TextView) v.findViewById(R.id.textViewDate);
                datum.setVisibility(View.VISIBLE);
                datum.setText(current.getDate());
            } else {
                v.findViewById(R.id.textViewDate).setVisibility(View.GONE);
                if (current.senderId == messagesArray[position - 1].senderId) {
                    v.findViewById(R.id.absender).setVisibility(View.GONE);
                    v.findViewById(R.id.space).setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return messagesArray.length;
        }

        private boolean gleicherTag(Date pDate1, Date pDate2) {
            Calendar c1 = new GregorianCalendar(), c2 = new GregorianCalendar();
            c1.setTime(pDate1);
            c2.setTime(pDate2);
            return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH) && c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH);
        }
    }

    private class SendMessage extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            if (chat != null && Utils.checkNetwork() && Utils.getMessengerDBConnection().isUserInChat(Utils.getCurrentUser(), chat) && !params[0].equals("")) {
                try {
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            new URL(generateURL(params[0]))
                                                    .openConnection()
                                                    .getInputStream(), "UTF-8"));
                    String erg = "";
                    String l;
                    Log.i("Tag", "Nachricht " + params[0]);
                    while ((l = reader.readLine()) != null)
                        erg += l;
                    Log.d("Debug", "result of send Message: " + erg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private String generateURL(String message) {
            message = message.replace(' ', '+').replace(System.getProperty("line.separator"), "_l_");
            return "http://moritz.liegmanns.de/messenger/send.php?key=5453&userid=" + Utils.getUserID() + "&message=" + message + "&chatid=" + chat.chatId;
        }
    }

    private class SendChatname extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (chat != null && Utils.checkNetwork())
                try {
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            new URL(generateURL())
                                                    .openConnection()
                                                    .getInputStream(), "UTF-8"));
                    String erg = "";
                    String l;
                    while ((l = reader.readLine()) != null)
                        erg += l;
                    Log.d("Debug", "result of send Chatname: " + erg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            return null;
        }

        private String generateURL() {
            String chatname = chat.chatName.replace(' ', '+');
            return "http://moritz.liegmanns.de/messenger/editChatname.php?key=5453&chatid=" + chat.chatId + "&chatname=" + chatname;
        }
    }
}