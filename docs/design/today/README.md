# Today screen designs

Reference designs for task 18. Each file is one state of the Today screen,
drawn at phone width (390 px).

How to read them:
- Layout and copy are in the HTML between <x-dc> and </x-dc>.
- `{{c.ever}}`, `{{c.muted}}` etc. are colour tokens. Their light and dark
  values are in the `<script>` block at the bottom of each file (object `P`)
  and match contract/design/tokens.json.
- `disc(...)` in the script shows how each day state is drawn:
  complete = filled evergreen, partial = half larch, missed = ring,
  rest = small dot in the centre, future = plain sunken disc,
  today = sunken disc with a larch ring.
- Font: Bricolage Grotesque for the app's voice. Literata italic for words
  the user wrote themselves (intention, milestone notes).

Files:
01 mid-day · 02 day complete · 03 rest day · 04 milestone ·
05 several milestones · 06 not started · 07 no arc · 08 ended
