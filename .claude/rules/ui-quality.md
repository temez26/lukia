# UI Quality

Problems that came up more than once in a polish pass. Check new UI for them before it lands.

- **No user-facing string literals in Kotlin.** Every visible or spoken string, including default parameter values, `onClickLabel`, `contentDescription` and loading messages, comes from `composeResources`. Counts use `<plurals>`, never `if (n == 1)`.
- **New strings go into every locale in the same change.** German and Dutch address the reader formally (Sie / u). Match that in new sentences, imperatives included ("Prüfen Sie", not "Prüfe").
- **Check new chrome at the largest system font size.** Fixed heights clip labels; grow with the text (see `MobileNavigationBar.labelGrowth`) or shrink to fit (`SegmentedControl`).
- **Form fields declare what they hold.** Sign-in fields pass an autofill `ContentType`, and the keyboard's action key moves to the next field with a `FocusRequester` (plain Next stops on the field's own clear button) and submits from the last one.
