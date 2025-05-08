package com.example.prog3client.Model;

import java.util.ArrayList;
import java.util.List;

public class Inbox {
    private List<Email> emails;

    public Inbox(){
        this.emails = new ArrayList<>();
    }

    public void aggiungiEmail(Email email) {
        emails.add(email);
    }

    public void rimuoviEmail(Email email) {
        emails.remove(email);
    }

    public void removeEmailById(String id) {
        emails.removeIf(email -> email.getId().equals(id));
    }

    public Email getEmailbyId(String id) {
        for (Email email : emails) {
            if (email.getId().equals(id)) {
                return email;
            }
        }
        return null; // Restituisce null se l'email non viene trovata
    }

    public List<Email> getEmails() {
        return new ArrayList<>(emails);
    }

    @Override
    public String toString() {
        return "Inbox{" +
                "emails=" + emails +
                '}';
    }
}
