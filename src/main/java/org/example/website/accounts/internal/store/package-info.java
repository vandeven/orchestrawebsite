/**
 * The data and where it is kept (ARC-5, ARC-6): the records (user, session, recovery link, login
 * counter), the four repository interfaces, and AccountsStorage, which chooses between the
 * in-memory and the Firestore implementation. The features use only the interfaces.
 */
package org.example.website.accounts.internal.store;
