package de.slg.stundenplan;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import de.slg.leoapp.R;

class WochentagAdapter extends ArrayAdapter<Fach> {
    private final Context cont;
    private final Fach[] fachAd;
    private final View[] viAd;

    WochentagAdapter(Context pCont, Fach[] pFach) {
        super(pCont, R.layout.list_item_schulstunde, pFach);
        cont = pCont;
        fachAd = pFach;
        viAd = new View[pFach.length];
    }

    @NonNull
    @Override
    public View getView(int position, View v, @NonNull ViewGroup parent) {
        if (position < fachAd.length && fachAd[0] != null) {
            if (v == null) {
                LayoutInflater layIn = (LayoutInflater) cont.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                int id2 = R.layout.list_item_schulstunde;
                v = layIn.inflate(id2, null);
            }

            TextView twFach = (TextView) v.findViewById(R.id.fach_wt);
            TextView twLehrer = (TextView) v.findViewById(R.id.lehrer_wt);
            TextView twRaum = (TextView) v.findViewById(R.id.raum_wt);
            TextView twStunde = (TextView) v.findViewById(R.id.stunde_wt);

            if (fachAd[position] != null) {


                if (fachAd[position].gibName().equals("") && !fachAd[position].gibNotiz().equals("notiz")) {
                    String[] sa = fachAd[position].gibNotiz().split(" ");
                    twFach.setText(sa[0]);
                } else {
                    twFach.setText(fachAd[position].gibName());
                }
                twLehrer.setText(fachAd[position].gibLehrer());
                twRaum.setText(fachAd[position].gibRaum());
                twStunde.setText(fachAd[position].gibStundenName());
            }
        }
        viAd[position] = v;
        return v;
    }
}
