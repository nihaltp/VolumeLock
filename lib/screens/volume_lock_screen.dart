import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/app_state.dart';
import '../services/volume_service.dart';

class VolumeLockScreen extends StatefulWidget {
  const VolumeLockScreen({super.key});

  @override
  State<VolumeLockScreen> createState() => _VolumeLockScreenState();
}

class _VolumeLockScreenState extends State<VolumeLockScreen> {
  Map<String, int> _currentVolumes = {};
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _refreshVolumes();
    // Poll current volumes every 2 seconds to keep the UI fresh.
    _pollTimer = Timer.periodic(
      const Duration(seconds: 2),
      (_) => _refreshVolumes(),
    );
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _refreshVolumes() async {
    final volumes = await VolumeService.getCurrentVolumes();
    if (mounted) setState(() => _currentVolumes = volumes);
  }

  Future<void> _toggleVolumeLock(AppState state, bool value) async {
    if (value) {
      // Check DND / notification policy access first.
      final hasAccess = await VolumeService.hasNotificationPolicyAccess();
      if (!hasAccess && mounted) {
        final grant = await showDialog<bool>(
          context: context,
          builder: (_) => const _PermissionDialog(
            title: 'Permission Required',
            content:
                'To lock notification and ring volumes, please grant '
                '"Do Not Disturb access" in the next screen.',
            actionLabel: 'Open Settings',
          ),
        );
        if (grant == true) {
          await VolumeService.openNotificationPolicySettings();
          return; // User must come back and toggle again after granting.
        }
      }
    }
    await state.setVolumeLockEnabled(value);
    if (value) _refreshVolumes();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final state = context.watch<AppState>();
    final enabled = state.volumeLockEnabled;
    final locked = state.lockedVolumes;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Volume Lock'),
        backgroundColor: theme.colorScheme.primary,
        foregroundColor: theme.colorScheme.onPrimary,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Toggle card
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Volume Lock',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          enabled
                              ? 'Active — volumes will be restored if changed '
                                  'while the screen is off.'
                              : 'When enabled, the current volumes are '
                                  'remembered when the screen turns off.',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Switch(
                    value: enabled,
                    onChanged: (v) => _toggleVolumeLock(state, v),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Current volumes
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Current Volumes',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 12),
                  _VolumeRow(
                    icon: Icons.music_note,
                    label: 'Media',
                    volume: _currentVolumes['media'],
                    maxVolume: _currentVolumes['mediaMax'],
                    lockedVolume: locked?.media,
                  ),
                  _VolumeRow(
                    icon: Icons.ring_volume,
                    label: 'Ring',
                    volume: _currentVolumes['ring'],
                    maxVolume: _currentVolumes['ringMax'],
                    lockedVolume: locked?.ring,
                  ),
                  _VolumeRow(
                    icon: Icons.notifications,
                    label: 'Notification',
                    volume: _currentVolumes['notification'],
                    maxVolume: _currentVolumes['notificationMax'],
                    lockedVolume: locked?.notification,
                  ),
                  _VolumeRow(
                    icon: Icons.alarm,
                    label: 'Alarm',
                    volume: _currentVolumes['alarm'],
                    maxVolume: _currentVolumes['alarmMax'],
                    lockedVolume: locked?.alarm,
                  ),
                ],
              ),
            ),
          ),

          if (enabled && locked != null) ...[
            const SizedBox(height: 16),
            Card(
              color: theme.colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(Icons.lock,
                        color: theme.colorScheme.onPrimaryContainer),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Locked volumes: Media ${locked.media}, '
                        'Ring ${locked.ring}, '
                        'Notification ${locked.notification}, '
                        'Alarm ${locked.alarm}',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          _InfoSection(
            title: 'How it works',
            bullets: const [
              'Enable Volume Lock to capture current volumes.',
              'When the screen turns off, those volumes are recorded.',
              'If any volume is changed while the screen is off, it is '
                  'automatically restored when the screen turns on.',
              'Disabling Volume Lock stops the monitoring service.',
            ],
          ),
        ],
      ),
    );
  }
}

// ─── Sub-widgets ─────────────────────────────────────────────────────────────

class _VolumeRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final int? volume;
  final int? maxVolume;
  final int? lockedVolume;

  const _VolumeRow({
    required this.icon,
    required this.label,
    this.volume,
    this.maxVolume,
    this.lockedVolume,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final v = volume ?? 0;
    final max = maxVolume ?? 15;
    final fraction = max > 0 ? v / max : 0.0;
    final isOverridden = lockedVolume != null && lockedVolume != v;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: theme.textTheme.bodyMedium,
            ),
          ),
          Expanded(
            child: LinearProgressIndicator(
              value: fraction.clamp(0.0, 1.0),
              minHeight: 6,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 36,
            child: Text(
              '$v',
              style: theme.textTheme.bodySmall,
              textAlign: TextAlign.right,
            ),
          ),
          if (isOverridden)
            Padding(
              padding: const EdgeInsets.only(left: 4),
              child: Tooltip(
                message: 'Locked at $lockedVolume',
                child: Icon(
                  Icons.lock_outline,
                  size: 14,
                  color: theme.colorScheme.error,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _InfoSection extends StatelessWidget {
  final String title;
  final List<String> bullets;

  const _InfoSection({required this.title, required this.bullets});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title,
                style: theme.textTheme.titleSmall
                    ?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ...bullets.map(
              (b) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('• '),
                    Expanded(
                      child: Text(b, style: theme.textTheme.bodySmall),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PermissionDialog extends StatelessWidget {
  final String title;
  final String content;
  final String actionLabel;

  const _PermissionDialog({
    required this.title,
    required this.content,
    required this.actionLabel,
  });

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(title),
      content: Text(content),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(actionLabel),
        ),
      ],
    );
  }
}
