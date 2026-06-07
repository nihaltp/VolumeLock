import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:volume_lock/models/app_state.dart';
import 'package:volume_lock/screens/app_volume_lock_screen.dart';
import 'package:volume_lock/screens/settings_screen.dart';
import 'package:volume_lock/screens/volume_lock_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final state = context.watch<AppState>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Volume Lock'),
        backgroundColor: theme.colorScheme.primary,
        foregroundColor: theme.colorScheme.onPrimary,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SizedBox(height: 8),
          _FeatureCard(
            icon: Icons.lock,
            title: 'Volume Lock',
            subtitle:
                'Lock all volume levels when the device screen turns off. '
                'Any changes made while the screen is off are reverted when '
                'the screen turns back on.',
            isEnabled: state.volumeLockEnabled,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const VolumeLockScreen(),
              ),
            ),
          ),
          const SizedBox(height: 16),
          _FeatureCard(
            icon: Icons.apps,
            title: 'App Volume Lock',
            subtitle: 'Remember the media volume for selected apps. When you '
                'return to a tracked app, its volume is automatically '
                'restored.',
            isEnabled: state.appVolumeLockEnabled,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const AppVolumeLockScreen(),
              ),
            ),
          ),
          const SizedBox(height: 16),
          _FeatureCard(
            icon: Icons.settings,
            title: 'Settings',
            subtitle: 'Configure app preferences.',
            isEnabled: state.loggingEnabled,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const SettingsScreen(),
              ),
            ),
          ),
          const SizedBox(height: 24),
          const _InfoCard(
            icon: Icons.info_outline,
            text: 'Both features run as foreground services and require '
                'certain permissions. Follow the in-app prompts to grant them.',
          ),
        ],
      ),
    );
  }
}

// ─── Helper widgets ────────────────────────────────────────────────────────

class _FeatureCard extends StatelessWidget {
  const _FeatureCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.isEnabled,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final bool isEnabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color =
        isEnabled ? theme.colorScheme.primary : theme.colorScheme.outline;

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              CircleAvatar(
                radius: 28,
                backgroundColor: color.withValues(alpha: 0.15),
                child: Icon(icon, color: color, size: 28),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            title,
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: isEnabled
                                ? theme.colorScheme.primaryContainer
                                : theme.colorScheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            isEnabled ? 'ON' : 'OFF',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: isEnabled
                                  ? theme.colorScheme.onPrimaryContainer
                                  : theme.colorScheme.onSurfaceVariant,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      subtitle,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(Icons.chevron_right, color: theme.colorScheme.outline),
            ],
          ),
        ),
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: theme.colorScheme.onSecondaryContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                text,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSecondaryContainer,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
