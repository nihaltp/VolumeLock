import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:volume_lock/main.dart';
import 'package:volume_lock/models/app_state.dart';
import 'package:volume_lock/screens/home_screen.dart';

void main() {
  group('VolumeLockApp', () {
    testWidgets('renders HomeScreen with Volume Lock and App Volume Lock cards',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        ChangeNotifierProvider(
          create: (_) => AppState(),
          child: const VolumeLockApp(),
        ),
      );

      // Wait for async initialisation (SharedPreferences stub returns immediately).
      await tester.pumpAndSettle();

      expect(find.text('Volume Lock'), findsWidgets);
      expect(find.text('App Volume Lock'), findsOneWidget);
    });

    testWidgets('tapping Volume Lock card navigates to VolumeLockScreen',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        ChangeNotifierProvider(
          create: (_) => AppState(),
          child: const VolumeLockApp(),
        ),
      );
      await tester.pumpAndSettle();

      // Tap the first card (Volume Lock).
      await tester.tap(find.text('Volume Lock').first);
      await tester.pumpAndSettle();

      // The Volume Lock screen has a Switch widget.
      expect(find.byType(Switch), findsOneWidget);
    });

    testWidgets('tapping App Volume Lock card navigates to AppVolumeLockScreen',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        ChangeNotifierProvider(
          create: (_) => AppState(),
          child: const VolumeLockApp(),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('App Volume Lock'));
      await tester.pumpAndSettle();

      expect(find.text('Select apps to track'), findsOneWidget);
    });
  });

  group('AppState', () {
    test('initial state has both locks disabled', () {
      final state = AppState();
      expect(state.volumeLockEnabled, isFalse);
      expect(state.appVolumeLockEnabled, isFalse);
      expect(state.lockedVolumes, isNull);
      expect(state.installedApps, isEmpty);
    });

    test('updateLockedVolumes stores snapshot and notifies listeners', () {
      final state = AppState();
      bool notified = false;
      state.addListener(() => notified = true);

      const snapshot = VolumeSnapshot(
        media: 8,
        ring: 5,
        notification: 5,
        alarm: 6,
      );
      state.updateLockedVolumes(snapshot);

      expect(state.lockedVolumes, isNotNull);
      expect(state.lockedVolumes!.media, 8);
      expect(notified, isTrue);
    });

    test('VolumeSnapshot toMap / fromMap round-trip', () {
      const original = VolumeSnapshot(
        media: 7,
        ring: 4,
        notification: 3,
        alarm: 6,
      );
      final map = original.toMap();
      final restored = VolumeSnapshot.fromMap(map);

      expect(restored.media, original.media);
      expect(restored.ring, original.ring);
      expect(restored.notification, original.notification);
      expect(restored.alarm, original.alarm);
    });
  });

  group('HomeScreen widgets', () {
    testWidgets('shows both feature cards with OFF badge when disabled',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: ChangeNotifierProvider(
            create: (_) => AppState(),
            child: const HomeScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('OFF'), findsNWidgets(2));
    });
  });
}
