package com.example.prog3client.Model;
import java.util.List;

public class Email {
    private String id;
    private String mittente;
    private List<String> destinatari;
    private String oggetto;
    private String testo;
    private String data;
    private boolean letta;

    public Email(String id, String mittente, List<String> destinatari, String oggetto, String testo, String data) {
        this.id = id;
        this.mittente = mittente;
        this.destinatari = destinatari;
        this.oggetto = oggetto;
        this.testo = testo;
        this.data = data;
        this.letta = false;
    }

    public String getId() {
        return id;
    }

    public String getMittente() {
        return mittente;
    }

    public List<String> getDestinatari() {
        return destinatari;
    }

    public String getOggetto() {
        return oggetto;
    }

    public String getTesto() {
        return testo;
    }

    public String getData() {
        return data;
    }

    public boolean isLetta() {
        return letta;
    }

    public void setLetta(boolean letta) {
        this.letta = letta;
    }

    @Override
    public String toString() {
        return "Da: " + mittente + " [" + oggetto +  "]";
    }
}