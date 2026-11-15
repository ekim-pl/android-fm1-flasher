# Optional Firebase analytics

These dependencies are included only in builds explicitly configured with Firebase analytics.
The standard Android build and the base iOS project do not include them.

- Android: Firebase BoM 34.6.0, Firebase Analytics 23.0.0 and Google Play services measurement dependencies.
  The Firebase Analytics Maven artifact declares the [Android Software Development Kit License](https://developer.android.com/studio/terms.html).
  [Artifact metadata](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-analytics/23.0.0/firebase-analytics-23.0.0.pom).
- Apple: Firebase Apple SDK 12.6.0, FirebaseAnalyticsCore and its transitive dependencies.
  The Firebase source license and component-specific third-party notices are maintained in the
  [Firebase Apple SDK repository](https://github.com/firebase/firebase-ios-sdk/tree/12.6.0).
  Binary measurement components have their own terms; the project's MIT license does not relicense them.

[Firebase terms](https://firebase.google.com/terms/) and [Google privacy policy](https://policies.google.com/privacy).
Advertising storage and personalization are disabled in this integration. Analytics is optional
and disabled until consent; see the application's privacy policy for the data inventory.
