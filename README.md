# Foodish Partner App

This application is for restaurant owners to manage their presence on the Foodish platform. It provides tools for menu management, order processing, and profile updates.

> **For a full overview of the entire 3-app ecosystem (including the User and Rider apps), please see the [main project repository](https://github.com/BAPPI-SWE/FOODISH).**


## 🚀 Key Features
* **Authentication & Onboarding:** Secure sign-up and a dedicated profile creation screen for restaurant details, including selecting from dynamic delivery locations.
* **Menu Management:** Full CRUD (Create, Read, Update, Delete) capabilities for both "Pre-Order" categories and "Current Menu" items.
* **Order Management Dashboard:**
    * A dashboard that shows a real-time badge with the count of new, incoming pre-orders for each category.
    * A dedicated order list screen with advanced features.
* **Advanced Order Processing:**
    * **Dynamic Filtering:** Filter incoming orders by the customer's specific sub-location.
    * **Bulk Actions:** Accept or reject all filtered orders at once.
    * **Individual Actions:** Accept or reject orders one-by-one, with visual confirmation.
* **Productivity Tools:**
    * **Print to PDF:** Generate a professionally formatted PDF of filtered orders for kitchen staff.
    * **Custom Push Notifications:** Send custom messages to all users with pending orders in a filtered list, powered by Firebase Cloud Functions.

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Architecture:** MVVM
* **Navigation:** Jetpack Navigation Compose
* **Backend:** Firebase (Authentication, Firestore, Cloud Functions)

## ⚙️ Setup
1.  Ensure you have a Firebase project set up.
2.  Add this app to your Firebase project with the package name `com.yumzy.partner`.
3.  Add your debug SHA-1 key to the Firebase project settings.
4.  Download the `google-services.json` file and place it in the `app/` directory.
5.  Build and run.
