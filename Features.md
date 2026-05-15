# JitterTravel

A tiny app to help me see the logistics of my travel, especially for trips involving lots of transport modes, multiple hotels, etc.

## UI

1. View layout week by week, maximizing the number of weeks displayable with all details showing
    a. Show current date: where am I
    b. (Nice to have) what's next?
2. Data entry forms for:
    a. Transport: flight, train, bus, taxi, walk
        - Provider (United, DB, Eurostar, etc.)
        - Number (Flight #, Train #, taxi/walk would be empty)
        - Start date/time (in zone of starting point)
        - End date/time (in zone of ending point) - OPTIONAL
        - Duration (for taxi/walk only)
    b. Lodging = hotel
        - Hotel name
        - Address
        - Check-in date
        - Check-out date
    c. Conference
        - Name
        - Address
        - Start date
        - End date
        - Committment: Speaking/Attending/Tentative
            = Tentative -> Confirm: Attending
            = Tentative -> Confirm: Speaking
            = Tentative -> CANCEL
            = Attending -> CANCEL
    d. Meetups/User Group/Presentations
        - Address
        - Date
        - Time
    e. (Nice to have) 1:1 meetings (coffee, dinner, etc.)
3. Editing of the above
4. Handles multiple bookings, e.g., multiple hotels during the same/overlapping time period
    a. (Nice to have) Highlight overlaps/conflicts

## Assumptions

Date/time is always assuming local time, i.e., wherever I am, what time is the thing.
This may mean for start-end transportation, the starting time zone is different from the ending one, but all that matters is what time is it where I am or where I'll be.



