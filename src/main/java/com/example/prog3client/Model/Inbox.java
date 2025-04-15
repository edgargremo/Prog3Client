package com.example.prog3client.Model;

import java.util.ArrayList;
import java.util.List;

public class Inbox {
    private List<Email> emails;


    public Inbox(){
        this.emails = new ArrayList<>();
    }

    // Aggiunge un'email all'inbox
    public void aggiungiEmail(Email email) {
        emails.add(email);
    }

    // Rimuove un'email dall'inbox
    public void rimuoviEmail(Email email) {
        emails.remove(email);
    }

    // Restituisce la lista di tutte le email nell'inbox
    public List<Email> getEmails() {
        return new ArrayList<>(emails); // Ritorna una copia per evitare modifiche esterne
    }

    // Cerca email per mittente
    public List<Email> cercaPerMittente(String mittente) {
        List<Email> risultato = new ArrayList<>();
        for (Email email : emails) {
            if (email.getMittente().equalsIgnoreCase(mittente)) {
                risultato.add(email);
            }
        }
        return risultato;
    }

    // Cerca email per oggetto
    public List<Email> cercaPerOggetto(String oggetto) {
        List<Email> risultato = new ArrayList<>();
        for (Email email : emails) {
            if (email.getOggetto().toLowerCase().contains(oggetto.toLowerCase())) {
                risultato.add(email);
            }
        }
        return risultato;
    }

    public Email getEmailById(String id) {
        for (Email email : emails) {
            if (email.getId().equals(id)) {
                return email;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Inbox{" +
                "emails=" + emails +
                '}';
    }
}
