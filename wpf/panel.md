# Panel

## Drop Panel

``` cs
public class DropPanel : Panel
{
    public static readonly DependencyProperty PositionProperty;
    public static readonly DependencyProperty DesiredPositionProperty;

    static DropPanel()
    {
        PositionProperty = DependencyProperty.RegisterAttached(
            "Position",
            typeof(Rect),
            typeof(FallsPanel),
            new FrameworkPropertyMetadata(
                new Rect(double.NaN, double.NaN, double.NaN, double.NaN),
                FrameworkPropertyMetadataOptions.AffectsParentArrange));

        DesiredPositionProperty = DependencyProperty.RegisterAttached(
            "DesiredPosition",
            typeof(Rect),
            typeof(FallsPanel),
            new FrameworkPropertyMetadata(
                new Rect(double.NaN, double.NaN, double.NaN, double.NaN),
                OnDesiredPositionChanged));

    }

    public static Rect GetPosition(DependencyObject obj)
    {
        return (Rect)obj.GetValue(PositionProperty);
    }

    public static void SetPosition(DependencyObject obj, Rect value)
    {
        obj.SetValue(PositionProperty, value);
    }

    private static void OnDesiredPositionChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        var desiredPosition = (Rect)e.NewValue;
        AnimateToPosition(d, desiredPosition);
    }

    public static Rect GetDesiredPosition(DependencyObject obj)
    {
        return (Rect)obj.GetValue(DesiredPositionProperty);
    }

    public static void SetDesiredPosition(DependencyObject obj, Rect value)
    {
        obj.SetValue(DesiredPositionProperty, value);
    }

    private static void AnimateToPosition(DependencyObject d, Rect desiredPosition)
    {
        var position = GetPosition(d);
        if (double.IsNaN(position.X))
        {
            SetPosition(d, desiredPosition);
            return;
        }

        var distance = Math.Max(
            (desiredPosition.TopLeft - position.TopLeft).Length,
            (desiredPosition.BottomRight - position.BottomRight).Length);

        var animationTime = TimeSpan.FromMilliseconds(700);//distance * 2);
        var animation = new RectAnimation(position, desiredPosition, new Duration(animationTime));
        animation.DecelerationRatio = 1;
        ((UIElement)d).BeginAnimation(PositionProperty, animation);
    }
    
    protected override Size MeasureOverride(Size availableSize)
    {
        foreach (UIElement child in InternalChildren)
        {
            child.Measure(availableSize);
        }

        var positions = new Point[InternalChildren.Count];
        var desiredHeight = ArrangeChildren(positions, availableSize.Width);

        int j = 0;
        foreach (UIElement child in InternalChildren)
        {
            SetDesiredPosition(child, new Rect(positions[j++], child.DesiredSize));
        }

        return new Size(availableSize.Width, desiredHeight);
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        foreach (UIElement child in InternalChildren)
        {
            var position = GetPosition(child);
            if (double.IsNaN(position.Top))
                position = GetDesiredPosition(child);
            child.Arrange(position);
        }

        return finalSize;
    }

    private double ArrangeChildren(Point[] positions, double availableWidth)
    {
        var width_length = 0;
        var current_width = 0d;
        var desired_height = 0d;
        var lock_width = false;

        for (int i = 0; i < InternalChildren.Count; i++)
        {
            var child = InternalChildren[i];
            var x = current_width;
            var y = 0d;
            
            if (lock_width == false)
            {
                if (current_width == 0d || current_width + child.DesiredSize.Width <= availableWidth)
                {
                    width_length += 1;
                    current_width += child.DesiredSize.Width;
                }
                else
                {
                    lock_width = true;
                }
            }
            
            if (i >= width_length)
            {
                x = positions[i - width_length].X;
                y = positions[i - width_length].Y + InternalChildren[i - width_length].DesiredSize.Heigh
            }

            desired_height = Math.Max(desired_height, child.DesiredSize.Height + y);
            positions[i] = new Point(x, y);
        }

        return desired_height;
    }
}
```
